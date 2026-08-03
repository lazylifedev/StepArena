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
  schemaVersion: 2, stableId: '0123456789abcdef0123456789abcdef', roomId: 'daily-room',
  localDate: '2026-08-03', zoneId: 'Asia/Tokyo', steps: 100, unclassifiedSteps: 0,
  unclassifiedStepsQuality: 'MEASURED', externalRecoveredSteps: 0, unallocatedMeasuredSteps: 0,
  distanceMeters: 70, walkingDurationSeconds: 60, caloriesKcal: 4, averageWalkingSpeedKmh: 4.2,
  stepsQuality: 'MEASURED', distanceQuality: 'MEASURED', durationQuality: 'MEASURED',
  caloriesQuality: 'ESTIMATED', speedQuality: 'MEASURED', activeHourCount: 1,
  walkingSessionCount: 1, finalized: true, finalizedAtEpochMillis: 1,
  createdAtEpochMillis: 1, updatedAtEpochMillis: 1, ...timestamps(), ...overrides,
});
const root = (overrides = {}) => ({
  schemaVersion: 2, minimumRestoreVersion: 2, appVersionName: '1.0.0-qa', appVersionCode: 1, databaseVersion: 10,
  backupGeneration: 1, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(),
  localTimeZone: 'Asia/Tokyo', dailyCount: 1, hourlyCount: 1, sessionCount: 1,
  challengeResultCount: 1, leagueHistoryCount: 1, leagueParticipantCount: 1, seasonHistoryCount: 1,
  integritySegmentCount: 1, achievementCount: 1, settingsCount: 1,
  newestLocalDate: '2026-08-03', ...timestamps(), ...overrides,
});
const hourly = (overrides = {}) => ({ schemaVersion: 2, stableId: '0123456789abcdef0123456789abcdef', roomId: 'hour-room', localDate: '2026-08-03', hourOfDay: 9,
  zoneId: 'Asia/Tokyo', utcOffsetSeconds: 32400, periodStartEpochMillis: 1,
  periodEndEpochMillis: 2, steps: 5, distanceMeters: 3.5, walkingDurationSeconds: 2,
  caloriesKcal: 1, averageWalkingSpeedKmh: 4, stepsQuality: 'MEASURED', distanceQuality: 'MEASURED',
  durationQuality: 'MEASURED', caloriesQuality: 'ESTIMATED', speedQuality: 'MEASURED', sensorEventCount: 1,
  recoveredSteps: 0, estimatedSteps: 0, appliedStepLengthMeters: .7, appliedWeightKg: 60,
  firstActivityAtEpochMillis: null, lastActivityAtEpochMillis: null,
  calorieFormulaVersion: 1, createdAtEpochMillis: 1, updatedAtEpochMillis: 2, ...timestamps(), ...overrides });
const session = (overrides = {}) => ({ schemaVersion: 2, stableId: '0123456789abcdef0123456789abcdef', roomId: 'session-room', localDate: '2026-08-03', zoneId: 'Asia/Tokyo',
  startedAtEpochMillis: 1, endedAtEpochMillis: 2, steps: 5, distanceMeters: 3,
  activeDurationSeconds: 1, elapsedDurationSeconds: 1, pausedDurationSeconds: 0, caloriesKcal: 1,
  averageMovingSpeedKmh: null, averageElapsedSpeedKmh: null,
  sessionType: 'MANUAL_WALK', status: 'COMPLETED', stepsQuality: 'MEASURED', distanceQuality: 'MEASURED',
  durationQuality: 'MEASURED', caloriesQuality: 'ESTIMATED', speedQuality: 'MEASURED', isManual: true,
  detectorEventCount: 1, estimatedStepCount: 0, recoveredStepCount: 0,
  createdAtEpochMillis: 1, updatedAtEpochMillis: 2, ...timestamps(), ...overrides });
const id = '0123456789abcdef0123456789abcdef';
const v2 = suffix => `userBackups/a/versions/v2${suffix || ''}`;

test('unauthenticated root write is denied', async () => assertFails(setDoc(doc(anon(), v2()), root())));
test('unauthenticated daily read is denied', async () => assertFails(getDoc(doc(anon(), v2('/daily/2026-08-03')))));
test('owner can create root metadata', async () => assertSucceeds(setDoc(doc(db('a'), v2()), root())));
test('owner can create daily data', async () => assertSucceeds(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily())));
test('other user cannot read root', async () => assertFails(getDoc(doc(db('b'), 'userBackups/a/versions/v2'))));
test('other user cannot read daily', async () => assertFails(getDoc(doc(db('b'), 'userBackups/a/versions/v2/daily/2026-08-03'))));
test('other user cannot write daily', async () => assertFails(setDoc(doc(db('b'), 'userBackups/a/versions/v2/daily/2026-08-03'), daily())));
test('owner collection listing is allowed for restore', async () => assertSucceeds(getDocs(collection(db('a'), v2('/daily')))));
test('unknown field is denied', async () => assertFails(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily({ secret: true }))));
test('missing required field is denied', async () => { const value = daily(); delete value.steps; await assertFails(setDoc(doc(db('a'), v2('/daily/2026-08-03')), value)); });
test('invalid type is denied', async () => assertFails(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily({ steps: '100' }))));
test('negative steps are denied', async () => assertFails(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily({ steps: -1 }))));
test('schema v1 new daily write is denied', async () => assertFails(setDoc(doc(db('a'), 'userBackups/a/daily/2026-08-03'), daily({ schemaVersion: 1 }))));
test('invalid daily id is denied', async () => assertFails(setDoc(doc(db('a'), v2('/daily/not-a-date')), daily())));
test('createdAt modification is denied', async () => { const ref = doc(db('a'), v2('/daily/2026-08-03')); await assertSucceeds(setDoc(ref, daily())); await assertFails(setDoc(ref, daily({ createdAt: serverTimestamp() }))); });
test('non-server updatedAt is denied', async () => assertFails(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily({ updatedAt: new Date(0) }))));
test('delete is denied', async () => { const ref = doc(db('a'), v2('/daily/2026-08-03')); await assertSucceeds(setDoc(ref, daily())); await assertFails(deleteDoc(ref)); });
test('unknown collection is denied', async () => assertFails(setDoc(doc(db('a'), v2('/private/x')), { ...timestamps() })));
test('valid hourly is allowed', async () => assertSucceeds(setDoc(doc(db('a'), v2('/hourly/2026-08-03-09')), hourly())));
test('invalid hourly id is denied', async () => assertFails(setDoc(doc(db('a'), v2('/hourly/2026-08-03-25')), hourly())));
test('valid completed session is allowed', async () => assertSucceeds(setDoc(doc(db('a'), v2(`/sessions/${id}`)), session())));
test('active session is denied', async () => assertFails(setDoc(doc(db('a'), v2(`/sessions/${id}`)), session({ status: 'ACTIVE' }))));
test('valid settings is allowed', async () => assertSucceeds(setDoc(doc(db('a'), v2('/settings/current')), { schemaVersion: 2, heightCm: 170, weightKg: 60, manualStepLengthMeters: null, useAutomaticStepLength: true, dailyStepGoal: 10000, ...timestamps() })));
test('complete root update is allowed', async () => { const ref = doc(db('a'), v2()); await assertSucceeds(setDoc(ref, root())); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); });
test('next generation can return root to in progress', async () => { const ref = doc(db('a'), v2()); await assertSucceeds(setDoc(ref, root())); let snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupGeneration: 2, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(), backupCompletedAt: deleteField(), updatedAt: serverTimestamp() }, { merge: true })); });
test('same deterministic daily id updates without duplication', async () => { const ref = doc(db('a'), v2('/daily/2026-08-03')); await assertSucceeds(setDoc(ref, daily())); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), steps: 101, updatedAtEpochMillis: 2, updatedAt: serverTimestamp() })); await env.withSecurityRulesDisabled(async context => assert.equal((await getDocs(collection(context.firestore(), v2('/daily')))).size, 1)); });

const challenge = (overrides={}) => ({ schemaVersion:2, stableId:id, roomId:'match-room', localDate:'2026-08-01', zoneId:'Asia/Tokyo', seasonId:'season-room', seasonStableId:id, matchType:'DAILY', status:'FINALIZED', outcome:'WIN', opponentTargetSteps:100, totalSteps:100, eligibleSteps:80, restrictedSteps:10, excludedSteps:10, opponentId:'opponent', opponentName:'Opponent', opponentAvatarKey:'walk', opponentRankTier:'BRONZE', opponentPersonality:'STEADY', restrictionReasons:'', competitiveQuality:'RESTRICTED', ratingBefore:1000, finalizedAtEpochMillis:2, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const league = (overrides={}) => ({ schemaVersion:2, stableId:id, roomId:'league-room', periodStart:'2026-07-20', periodEnd:'2026-07-26', zoneId:'Asia/Tokyo', status:'FINALIZED', points:12, rank:null, participantsJson:'[]', finalizedAtEpochMillis:2, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const season = (overrides={}) => ({ schemaVersion:2, stableId:id, roomId:'season-room', startedAtEpochMillis:1, endedAtEpochMillis:2, status:'FINALIZED', startRating:1000, wins:1, losses:0, draws:0, highestRankTier:'BRONZE', totalEligibleSteps:100, bestWinStreak:1, rewardClaimed:false, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const integrity = (overrides={}) => ({ schemaVersion:2, stableId:id, roomId:'integrity-room', localDate:'2026-08-01', zoneId:'Asia/Tokyo', startedAtEpochMillis:1, endedAtEpochMillis:2, totalSteps:100, eligibleSteps:80, restrictedSteps:10, excludedSteps:10, assessment:'LIMITED', reasons:'RECOVERED_LIMITED', classifierVersion:1, createdAtEpochMillis:1, ...timestamps(), ...overrides });

test('valid v2 challenge is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/challengeResults/${id}`)),challenge())));
test('invalid challenge enum is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/challengeResults/${id}`)),challenge({outcome:'UNKNOWN'}))));
test('valid v2 league is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/leagueHistory/${id}`)),league())));
test('valid v2 season is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/seasonHistory/${id}`)),season())));
test('valid v2 integrity is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity())));
test('integrity total mismatch is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity({eligibleSteps:79}))));
test('stable id mismatch is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity({stableId:'abcdef0123456789abcdef0123456789'}))));
test('existing schema v1 owner read remains allowed', async()=>{ await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a/daily/2026-07-01'),{schemaVersion:1})); await assertSucceeds(getDoc(doc(db('a'),'userBackups/a/daily/2026-07-01'))); });
test('existing schema v1 root write and update are denied', async()=>{ const ref=doc(db('a'),'userBackups/a'); await assertFails(setDoc(ref,{schemaVersion:1})); await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a'),{schemaVersion:1,generation:7})); await assertFails(setDoc(ref,{schemaVersion:1,generation:8})); });
test('v1 and v2 coexist and v2 writes leave v1 unchanged', async()=>{ const legacy={schemaVersion:1,steps:77}; await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a/daily/2026-07-01'),legacy)); await assertSucceeds(setDoc(doc(db('a'),v2()),root())); await assertSucceeds(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily())); assert.deepEqual((await getDoc(doc(db('a'),'userBackups/a/daily/2026-07-01'))).data(),legacy); });
