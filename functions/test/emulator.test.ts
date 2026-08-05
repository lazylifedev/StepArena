import {afterAll, beforeAll, describe, expect, it} from 'vitest';
import {getApps, initializeApp} from 'firebase-admin/app';
import {getFirestore, Timestamp} from 'firebase-admin/firestore';
import {finalizeExpiredChallenges} from '../src/services/finalize';

const projectId = 'demo-steparena-backend';
const functionsUrl = 'http://127.0.0.1:5001/demo-steparena-backend/us-central1';
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key';

async function createUser() {
  const response = await fetch(authUrl, {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify({returnSecureToken: true})});
  expect(response.ok).toBe(true);
  return await response.json() as {localId: string; idToken: string};
}

async function call(name: string, token: string, data: Record<string, unknown>) {
  const response = await fetch(`${functionsUrl}/${name}`, {method: 'POST', headers: {'content-type': 'application/json', authorization: `Bearer ${token}`}, body: JSON.stringify({data})});
  const text = await response.text();
  let body: {data?: Record<string, unknown>; result?: Record<string, unknown>; error?: Record<string, unknown>};
  try { body = JSON.parse(text) as typeof body; } catch { throw new Error(`callable returned ${response.status}: ${text}`); }
  return {status: response.status, body};
}

async function seed(path: string, data: Record<string, unknown>) { await dbForTest().doc(path).set(data); }
let dbForTest: () => ReturnType<typeof getFirestore>;

describe('Functions emulator integration', {timeout: 30000}, () => {
  let db: ReturnType<typeof getFirestore>;
  const created: string[] = [];
  beforeAll(() => {
    process.env.GCLOUD_PROJECT = projectId;
    process.env.GOOGLE_CLOUD_PROJECT = projectId;
    if (!getApps().length) initializeApp({projectId});
    db = getFirestore();
    dbForTest = () => db;
  });
  afterAll(async () => {
    await Promise.all(created.map(uid => db.doc(`officialProgress/${uid}/days/2026-08-05`).delete()));
  });

  it('loads the built entrypoint and accepts authenticated progress through the emulator', async () => {
    const user = await createUser();
    created.push(user.localId);
    const result = await call('submitOfficialProgress', user.idToken, {localDate: '2026-08-05', timezone: 'Asia/Tokyo', totalSteps: 1200, eligibleSteps: 1000, restrictedSteps: 200, excludedSteps: 0, integrityVersion: 1, sourceRevision: '1', requestId: `emulator-${user.localId}`});
    expect(result.status).toBe(200);
    expect((result.body.data ?? result.body.result)?.status, JSON.stringify(result.body)).toBe('accepted');
    const stored = await db.doc(`officialProgress/${user.localId}/days/2026-08-05`).get();
    expect(stored.data()?.officialSteps).toBe(1000);
  });

  it('rejects unauthenticated progress at the callable boundary', async () => {
    const result = await call('submitOfficialProgress', '', {localDate: '2026-08-05', timezone: 'Asia/Tokyo', totalSteps: 0, eligibleSteps: 0, restrictedSteps: 0, excludedSteps: 0, integrityVersion: 1, sourceRevision: '1', requestId: 'unauthenticated'});
    expect(result.status).toBe(401);
    expect(result.body.error?.status).toBe('UNAUTHENTICATED');
  });

  it('preserves idempotency and rejects stale revisions without changing the stored document', async () => {
    const user = await createUser();
    created.push(user.localId);
    const base = {localDate: '2026-08-05', timezone: 'Asia/Tokyo', totalSteps: 1200, eligibleSteps: 1000, restrictedSteps: 200, excludedSteps: 0, integrityVersion: 1, sourceRevision: '2', requestId: `revision-${user.localId}`};
    expect((await call('submitOfficialProgress', user.idToken, base)).body.result?.status).toBe('accepted');
    expect((await call('submitOfficialProgress', user.idToken, base)).body.result?.status).toBe('duplicate');
    const stale = {...base, sourceRevision: '1', requestId: `revision-stale-${user.localId}`, eligibleSteps: 900, restrictedSteps: 300};
    expect((await call('submitOfficialProgress', user.idToken, stale)).body.result?.status).toBe('stale');
    expect((await db.doc(`officialProgress/${user.localId}/days/2026-08-05`).get()).data()?.officialSteps).toBe(1000);
  });

  it('finds a same-division partner using Firestore emulator data and excludes the caller', async () => {
    const me = await createUser();
    const partner = await createUser();
    const otherDivision = await createUser();
    created.push(me.localId, partner.localId, otherDivision.localId);
    const now = new Date();
    await Promise.all([
      seed(`matchProfiles/${me.localId}`, {matchingStatus: 'available', league: 'silver', division: 2, recentOfficialSteps: 10000, lastActiveAt: now}),
      seed(`matchProfiles/${partner.localId}`, {matchingStatus: 'available', league: 'silver', division: 2, recentOfficialSteps: 10050, lastActiveAt: now}),
      seed(`matchProfiles/${otherDivision.localId}`, {matchingStatus: 'available', league: 'silver', division: 3, recentOfficialSteps: 10001, lastActiveAt: now}),
    ]);
    const result = await call('findChallengePartner', me.idToken, {});
    expect(result.body.result?.uid).toBe(partner.localId);
  });

  it('creates one challenge transactionally and rejects a duplicate request', async () => {
    const a = await createUser();
    const b = await createUser();
    created.push(a.localId, b.localId);
    await Promise.all([seed(`matchProfiles/${a.localId}`, {matchingStatus: 'available', league: 'gold', division: 1}), seed(`matchProfiles/${b.localId}`, {matchingStatus: 'available', league: 'gold', division: 1})]);
    const requestId = `challenge-${a.localId}`;
    const first = await call('createChallengeCallable', a.idToken, {partnerUid: b.localId, requestId});
    expect(first.body.result?.challengeId).toEqual(expect.any(String));
    const second = await call('createChallengeCallable', a.idToken, {partnerUid: b.localId, requestId});
    expect(second.body.error?.status).toBe('FAILED_PRECONDITION');
    const challenge = await db.collection('challenges').doc(first.body.result?.challengeId as string).get();
    expect(challenge.data()?.participantIds).toEqual([a.localId, b.localId]);
    expect(challenge.data()?.status).toBe('active');
  });

  it('allows only one active challenge when two requests race for the same users', async () => {
    const a = await createUser();
    const b = await createUser();
    created.push(a.localId, b.localId);
    await Promise.all([seed(`matchProfiles/${a.localId}`, {matchingStatus: 'available', league: 'race', division: 1}), seed(`matchProfiles/${b.localId}`, {matchingStatus: 'available', league: 'race', division: 1})]);
    const [left, right] = await Promise.all([
      call('createChallengeCallable', a.idToken, {partnerUid: b.localId, requestId: `race-a-${a.localId}`}),
      call('createChallengeCallable', b.idToken, {partnerUid: a.localId, requestId: `race-b-${b.localId}`}),
    ]);
    const successes = [left, right].filter(result => typeof result.body.result?.challengeId === 'string');
    expect(successes).toHaveLength(1);
    expect([left, right].some(result => result.body.error?.status === 'FAILED_PRECONDITION')).toBe(true);
    const challenges = await db.collection('challenges').where('participantIds', 'array-contains', a.localId).get();
    expect(challenges.docs.filter(doc => (doc.data().participantIds as string[]).includes(b.localId))).toHaveLength(1);
  });

  it('finalizes expired challenges through the production service and is idempotent', async () => {
    const now = Timestamp.fromMillis(Date.now());
    const seedChallenge = async (id: string, a: number, b: number, endsAt = now) => {
      const ref = db.doc(`challenges/${id}`);
      await ref.set({participantIds: [`${id}-a`, `${id}-b`], status: 'active', endsAt, startedAt: now});
      await Promise.all([
        ref.collection('participants').doc(`${id}-a`).set({uid: `${id}-a`, officialSteps: a, result: 'pending', syncState: 'pending'}),
        ref.collection('participants').doc(`${id}-b`).set({uid: `${id}-b`, officialSteps: b, result: 'pending', syncState: 'pending'}),
      ]);
      return ref;
    };
    const notExpired = await seedChallenge('not-expired', 32000, 38000, Timestamp.fromMillis(now.toMillis() + 60000));
    const win = await seedChallenge('winner', 32000, 38000);
    const draw = await seedChallenge('draw', 100000, 120000);
    const malformed = db.doc('challenges/malformed');
    await malformed.set({participantIds: ['malformed-a'], status: 'active', endsAt: now});
    const count = await finalizeExpiredChallenges(db, now);
    expect(count).toBe(2);
    expect((await notExpired.get()).data()?.status).toBe('active');
    const winData = (await win.get()).data();
    expect(winData?.status).toBe('finalized');
    expect(winData?.winnerUid).toBe('winner-b');
    expect((await win.collection('participants').doc('winner-a').get()).data()).toMatchObject({competitionSteps: 32000, rewardSteps: 30000, result: 'loss'});
    expect((await win.collection('participants').doc('winner-b').get()).data()).toMatchObject({competitionSteps: 38000, rewardSteps: 30000, result: 'win'});
    expect((await draw.get()).data()).toMatchObject({status: 'finalized', winnerUid: null});
    expect((await draw.collection('participants').doc('draw-b').get()).data()).toMatchObject({competitionSteps: 100000, rewardSteps: 30000, result: 'draw'});
    expect((await malformed.get()).data()?.status).toBe('active');
    expect(await finalizeExpiredChallenges(db, now)).toBe(0);
  });
});
