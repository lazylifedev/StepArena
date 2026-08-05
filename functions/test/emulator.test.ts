import {afterAll, beforeAll, describe, expect, it} from 'vitest';
import {getApps, initializeApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';

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

describe('Functions emulator integration', () => {
  let db: ReturnType<typeof getFirestore>;
  const created: string[] = [];
  beforeAll(() => {
    process.env.GCLOUD_PROJECT = projectId;
    process.env.GOOGLE_CLOUD_PROJECT = projectId;
    if (!getApps().length) initializeApp({projectId});
    db = getFirestore();
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
});
