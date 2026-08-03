const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, deleteDoc, serverTimestamp, deleteField } = require('firebase/firestore');

let env;
const projectId = 'steparena-rules-test';
const rulesPath = path.resolve(__dirname, '..', 'firestore.rules');

before(async () => {
  assert.equal(fs.existsSync(rulesPath), true, 'firestore.rules must exist');
  const rules = fs.readFileSync(rulesPath, 'utf8');
  assert.match(rules, /rules_version/);
  env = await initializeTestEnvironment({ projectId, firestore: { rules } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const db = uid => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const timestamps = () => ({ createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
const daily = (overrides = {}) => ({
  schemaVersion: 1, localDate: '2026-08-03', zoneId: 'Asia/Tokyo', steps: 100,
  externalRecoveredSteps: 0, unallocatedMeasuredSteps: 0, distanceMeters: 70,
  walkingDurationSeconds: 60, caloriesKcal: 4, stepsQuality: 'MEASURED', finalized: true,
  finalizedAtEpochMillis: 1, updatedAtEpochMillis: 1, integrityTotal: 100,
  integrityEligible: 80, integrityRestricted: 10, integrityExcluded: 10,
  integrityReasons: [], classifierVersion: 1, ...timestamps(), ...overrides,
});
const root = (overrides = {}) => ({
  schemaVersion: 1, appVersionName: '1.0.0-qa', appVersionCode: 1, databaseVersion: 10,
  backupGeneration: 1, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(),
  localTimeZone: 'Asia/Tokyo', dailyCount: 1, hourlyCount: 1, sessionCount: 1,
  challengeResultCount: 1, leagueHistoryCount: 1, achievementCount: 1,
  newestLocalDate: '2026-08-03', ...timestamps(), ...overrides,
});
const hourly = (overrides = {}) => ({ schemaVersion: 1, localDate: '2026-08-03', hourOfDay: 9,
  zoneId: 'Asia/Tokyo', utcOffsetSeconds: 32400, periodStartEpochMillis: 1,
  periodEndEpochMillis: 2, steps: 5, distanceMeters: 3.5, walkingDurationSeconds: 2,
  caloriesKcal: 1, stepsQuality: 'MEASURED', updatedAtEpochMillis: 2, ...timestamps(), ...overrides });
const session = (overrides = {}) => ({ schemaVersion: 1, localDate: '2026-08-03', zoneId: 'Asia/Tokyo',
  startedAtEpochMillis: 1, endedAtEpochMillis: 2, steps: 5, distanceMeters: 3,
  activeDurationSeconds: 1, elapsedDurationSeconds: 1, caloriesKcal: 1, sessionType: 'MANUAL',
  status: 'COMPLETED', stepsQuality: 'MEASURED', updatedAtEpochMillis: 2, ...timestamps(), ...overrides });
const id = '0123456789abcdef0123456789abcdef';

test('unauthenticated root write is denied', async () => assertFails(setDoc(doc(anon(), 'userBackups/a'), root())));
test('unauthenticated daily read is denied', async () => assertFails(getDoc(doc(anon(), 'userBackups/a/daily/2026-08-03'))));
test('owner can create root metadata', async () => assertSucceeds(setDoc(doc(db('a'), 'userBackups/a'), root())));
test('owner can create daily data', async () => assertSucceeds(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily())));
test('other user cannot read root', async () => assertFails(getDoc(doc(db('a'), 'userBackups/b'))));
test('other user cannot read daily', async () => assertFails(getDoc(doc(db('a'), 'userBackups/b/daily/2026-08-03'))));
test('other user cannot write daily', async () => assertFails(setDoc(doc(db('a'), 'userBackups/b/daily/2026-08-03'), daily())));
test('collection listing is denied', async () => assertFails(getDocs(collection(db('a'), 'userBackups/a/daily'))));
test('unknown field is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ secret: true }))));
test('missing required field is denied', async () => { const value = daily(); delete value.steps; await assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), value)); });
test('invalid type is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ steps: '100' }))));
test('negative steps are denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ steps: -1 }))));
test('integrity mismatch is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ integrityEligible: 79 }))));
test('invalid daily id is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/not-a-date'), daily())));
test('createdAt modification is denied', async () => { const ref = doc(db('a'), 'userBackups/a/daily/2026-08-03'); await assertSucceeds(setDoc(ref, daily())); await assertFails(setDoc(ref, daily({ createdAt: serverTimestamp() }))); });
test('non-server updatedAt is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ updatedAt: new Date(0) }))));
test('delete is denied', async () => { const ref = doc(db('a'), 'userBackups/a/daily/2026-08-03'); await assertSucceeds(setDoc(ref, daily())); await assertFails(deleteDoc(ref)); });
test('unknown collection is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/private/x'), { ...timestamps() })));
test('valid hourly is allowed', async () => assertSucceeds(setDoc(doc(db('a'), 'userBackups/a/hourly/2026-08-03-09'), hourly())));
test('invalid hourly id is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/hourly/2026-08-03-25'), hourly())));
test('valid completed session is allowed', async () => assertSucceeds(setDoc(doc(db('a'), `userBackups/a/sessions/${id}`), session())));
test('active session is denied', async () => assertFails(setDoc(doc(db('a'), `userBackups/a/sessions/${id}`), session({ status: 'ACTIVE' }))));
test('valid settings is allowed', async () => assertSucceeds(setDoc(doc(db('a'), 'userBackups/a/settings/current'), { schemaVersion: 1, heightCm: 170, weightKg: 60, manualStepLengthMeters: null, useAutomaticStepLength: true, dailyStepGoal: 10000, ...timestamps() })));
test('complete root update is allowed', async () => { const ref = doc(db('a'), 'userBackups/a'); await assertSucceeds(setDoc(ref, root())); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); });
test('next generation can return root to in progress', async () => { const ref = doc(db('a'), 'userBackups/a'); await assertSucceeds(setDoc(ref, root())); let snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupGeneration: 2, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(), backupCompletedAt: deleteField(), updatedAt: serverTimestamp() }, { merge: true })); });
test('same deterministic daily id updates without duplication', async () => { const ref = doc(db('a'), 'userBackups/a/daily/2026-08-03'); await assertSucceeds(setDoc(ref, daily())); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), steps: 101, updatedAtEpochMillis: 2, updatedAt: serverTimestamp() })); await env.withSecurityRulesDisabled(async context => assert.equal((await getDocs(collection(context.firestore(), 'userBackups/a/daily'))).size, 1)); });
