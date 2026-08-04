const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, deleteDoc, serverTimestamp, deleteField, Timestamp } = require('firebase/firestore');

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
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), v2()), root()));
});
const db = uid => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const timestamps = () => ({ createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
const op1 = '11111111-1111-4111-8111-111111111111';
const op2 = '22222222-2222-4222-8222-222222222222';
const daily = (overrides = {}) => ({
  schemaVersion: 2, backupGeneration: 1, stableId: '0123456789abcdef0123456789abcdef', roomId: 'daily-room',
  localDate: '2026-08-03', zoneId: 'Asia/Tokyo', steps: 100, unclassifiedSteps: 0,
  unclassifiedStepsQuality: 'MEASURED', externalRecoveredSteps: 0, unallocatedMeasuredSteps: 0,
  distanceMeters: 70, walkingDurationSeconds: 60, caloriesKcal: 4, averageWalkingSpeedKmh: 4.2,
  stepsQuality: 'MEASURED', distanceQuality: 'MEASURED', durationQuality: 'MEASURED',
  caloriesQuality: 'ESTIMATED', speedQuality: 'MEASURED', activeHourCount: 1,
  walkingSessionCount: 1, finalized: true, finalizedAtEpochMillis: 1,
  createdAtEpochMillis: 1, updatedAtEpochMillis: 1, ...timestamps(), ...overrides,
});
const root = (overrides = {}) => ({
  schemaVersion: 2, childGenerationVersion: 1, minimumRestoreVersion: 2, appVersionName: '1.0.0-qa', appVersionCode: 1, databaseVersion: 10,
  backupGeneration: 1, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(),
  leaseVersion: 1, backupOperationId: op1, leaseUpdatedAt: serverTimestamp(),
  localTimeZone: 'Asia/Tokyo', dailyCount: 1, hourlyCount: 1, sessionCount: 1,
  challengeResultCount: 1, leagueHistoryCount: 1, leagueParticipantCount: 1, seasonHistoryCount: 1,
  integritySegmentCount: 1, achievementCount: 1, settingsCount: 1,
  newestLocalDate: '2026-08-03', ...timestamps(), ...overrides,
});
const hourly = (overrides = {}) => ({ schemaVersion: 2, backupGeneration: 1, stableId: '0123456789abcdef0123456789abcdef', roomId: 'hour-room', localDate: '2026-08-03', hourOfDay: 9,
  zoneId: 'Asia/Tokyo', utcOffsetSeconds: 32400, periodStartEpochMillis: 1,
  periodEndEpochMillis: 2, steps: 5, distanceMeters: 3.5, walkingDurationSeconds: 2,
  caloriesKcal: 1, averageWalkingSpeedKmh: 4, stepsQuality: 'MEASURED', distanceQuality: 'MEASURED',
  durationQuality: 'MEASURED', caloriesQuality: 'ESTIMATED', speedQuality: 'MEASURED', sensorEventCount: 1,
  recoveredSteps: 0, estimatedSteps: 0, appliedStepLengthMeters: .7, appliedWeightKg: 60,
  firstActivityAtEpochMillis: null, lastActivityAtEpochMillis: null,
  calorieFormulaVersion: 1, createdAtEpochMillis: 1, updatedAtEpochMillis: 2, ...timestamps(), ...overrides });
const session = (overrides = {}) => ({ schemaVersion: 2, backupGeneration: 1, stableId: '0123456789abcdef0123456789abcdef', roomId: 'session-room', localDate: '2026-08-03', zoneId: 'Asia/Tokyo',
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
test('owner can create root metadata', async () => { await env.clearFirestore(); await assertSucceeds(setDoc(doc(db('a'), v2()), root())); });
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
test('valid settings is allowed', async () => assertSucceeds(setDoc(doc(db('a'), v2('/settings/current')), { schemaVersion: 2, backupGeneration: 1, heightCm: 170, weightKg: 60, manualStepLengthMeters: null, useAutomaticStepLength: true, dailyStepGoal: 10000, ...timestamps() })));
test('complete root update is allowed', async () => { const ref = doc(db('a'), v2()); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); });
test('next generation can return root to in progress', async () => { const ref = doc(db('a'), v2()); let snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupStatus: 'complete', backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp() })); snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), backupGeneration: 2, backupOperationId: op2, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(), leaseUpdatedAt: serverTimestamp(), backupCompletedAt: deleteField(), updatedAt: serverTimestamp() }, { merge: true })); });
test('cloud generation five advances consistently to six and completes', async () => {
  const ref = doc(db('a'), v2());
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), v2()), {
    ...root({ backupGeneration: 5, backupStatus: 'complete' }),
    backupStartedAt: Timestamp.fromMillis(1), backupCompletedAt: Timestamp.fromMillis(2),
    createdAt: Timestamp.fromMillis(1), updatedAt: Timestamp.fromMillis(2),
  }));
  let snap = await getDoc(ref);
  await assertSucceeds(setDoc(ref, {
    ...snap.data(), backupGeneration: 6, backupOperationId: op2, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(), leaseUpdatedAt: serverTimestamp(),
    backupCompletedAt: deleteField(), updatedAt: serverTimestamp(),
  }, { merge: true }));
  await assertSucceeds(setDoc(doc(db('a'), v2('/daily/2026-08-03')), daily({ backupGeneration: 6 })));
  snap = await getDoc(ref);
  await assertSucceeds(setDoc(ref, {
    ...snap.data(), backupGeneration: 6, backupStatus: 'complete',
    backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  }, { merge: true }));
});
test('cloud generation five rejects a generation one restart', async () => {
  const ref = doc(db('a'), v2());
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), v2()), {
    ...root({ backupGeneration: 5, backupStatus: 'complete' }),
    backupStartedAt: Timestamp.fromMillis(1), backupCompletedAt: Timestamp.fromMillis(2),
    createdAt: Timestamp.fromMillis(1), updatedAt: Timestamp.fromMillis(2),
  }));
  const snap = await getDoc(ref);
  await assertFails(setDoc(ref, {
    ...snap.data(), backupGeneration: 1, backupStatus: 'in_progress', backupStartedAt: serverTimestamp(),
    backupCompletedAt: deleteField(), updatedAt: serverTimestamp(),
  }, { merge: true }));
});
test('root complete rejects a generation different from in progress', async () => {
  const ref = doc(db('a'), v2());
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), v2()), { ...root({ backupGeneration: 6 }), backupStartedAt: Timestamp.now(), leaseUpdatedAt: Timestamp.now(), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
  const snap = await getDoc(ref);
  await assertFails(setDoc(ref, {
    ...snap.data(), backupGeneration: 7, backupStatus: 'complete',
    backupCompletedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  }, { merge: true }));
});
test('same deterministic daily id updates without duplication', async () => { const ref = doc(db('a'), v2('/daily/2026-08-03')); await assertSucceeds(setDoc(ref, daily())); const snap = await getDoc(ref); await assertSucceeds(setDoc(ref, { ...snap.data(), steps: 101, updatedAtEpochMillis: 2, updatedAt: serverTimestamp() })); await env.withSecurityRulesDisabled(async context => assert.equal((await getDocs(collection(context.firestore(), v2('/daily')))).size, 1)); });

const challenge = (overrides={}) => ({ schemaVersion:2, backupGeneration:1, stableId:id, roomId:'match-room', localDate:'2026-08-01', zoneId:'Asia/Tokyo', seasonId:'season-room', seasonStableId:id, matchType:'DAILY', status:'FINALIZED', outcome:'WIN', opponentTargetSteps:100, totalSteps:100, eligibleSteps:80, restrictedSteps:10, excludedSteps:10, opponentId:'opponent', opponentName:'Opponent', opponentAvatarKey:'walk', opponentRankTier:'BRONZE', opponentPersonality:'STEADY', restrictionReasons:'', competitiveQuality:'RESTRICTED', ratingBefore:1000, finalizedAtEpochMillis:2, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const league = (overrides={}) => ({ schemaVersion:2, backupGeneration:1, stableId:id, roomId:'league-room', periodStart:'2026-07-20', periodEnd:'2026-07-26', zoneId:'Asia/Tokyo', status:'FINALIZED', points:12, rank:null, participantsJson:'[]', finalizedAtEpochMillis:2, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const season = (overrides={}) => ({ schemaVersion:2, backupGeneration:1, stableId:id, roomId:'season-room', startedAtEpochMillis:1, endedAtEpochMillis:2, status:'FINALIZED', startRating:1000, wins:1, losses:0, draws:0, highestRankTier:'BRONZE', totalEligibleSteps:100, bestWinStreak:1, rewardClaimed:false, createdAtEpochMillis:1, updatedAtEpochMillis:2, ...timestamps(), ...overrides });
const integrity = (overrides={}) => ({ schemaVersion:2, backupGeneration:1, stableId:id, roomId:'integrity-room', localDate:'2026-08-01', zoneId:'Asia/Tokyo', startedAtEpochMillis:1, endedAtEpochMillis:2, totalSteps:100, eligibleSteps:80, restrictedSteps:10, excludedSteps:10, assessment:'LIMITED', reasons:'RECOVERED_LIMITED', classifierVersion:1, createdAtEpochMillis:1, ...timestamps(), ...overrides });

test('valid v2 challenge is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/challengeResults/${id}`)),challenge())));
test('invalid challenge enum is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/challengeResults/${id}`)),challenge({outcome:'UNKNOWN'}))));
test('valid v2 league is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/leagueHistory/${id}`)),league())));
test('valid v2 season is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/seasonHistory/${id}`)),season())));
test('valid v2 integrity is allowed', async()=>assertSucceeds(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity())));
test('integrity total mismatch is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity({eligibleSteps:79}))));
test('stable id mismatch is denied', async()=>assertFails(setDoc(doc(db('a'),v2(`/integritySegments/${id}`)),integrity({stableId:'abcdef0123456789abcdef0123456789'}))));
test('existing schema v1 owner read remains allowed', async()=>{ await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a/daily/2026-07-01'),{schemaVersion:1})); await assertSucceeds(getDoc(doc(db('a'),'userBackups/a/daily/2026-07-01'))); });
test('existing schema v1 root write and update are denied', async()=>{ const ref=doc(db('a'),'userBackups/a'); await assertFails(setDoc(ref,{schemaVersion:1})); await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a'),{schemaVersion:1,generation:7})); await assertFails(setDoc(ref,{schemaVersion:1,generation:8})); });
test('v1 and v2 coexist and v2 writes leave v1 unchanged', async()=>{ const legacy={schemaVersion:1,steps:77}; await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'userBackups/a/daily/2026-07-01'),legacy)); await assertSucceeds(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily())); assert.deepEqual((await getDoc(doc(db('a'),'userBackups/a/daily/2026-07-01'))).data(),legacy); });
test('owner can read a residual challenge document when root count is zero', async()=>{
  await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),v2()),root({challengeResultCount:0})));
  await assertSucceeds(setDoc(doc(db('a'),v2(`/challengeResults/${id}`)),challenge()));
  assert.equal((await getDocs(collection(db('a'),v2('/challengeResults')))).size,1);
});
test('invalid child generation version is denied', async()=>{ await env.clearFirestore(); await assertFails(setDoc(doc(db('a'),v2()),root({childGenerationVersion:2}))); });
test('untagged child create is denied', async()=>{ const value=daily(); delete value.backupGeneration; await assertFails(setDoc(doc(db('a'),v2('/daily/2026-08-03')),value)); });
test('wrong generation child create is denied', async()=>assertFails(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily({backupGeneration:2}))));
test('child write while parent complete is denied', async()=>{ const ref=doc(db('a'),v2()); const snap=await getDoc(ref); await assertSucceeds(setDoc(ref,{...snap.data(),backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); await assertFails(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily())); });
test('legacy v2 root migrates to generation tagged in progress', async()=>{
  await env.withSecurityRulesDisabled(async c=>{ const legacy=root({backupGeneration:7,backupStatus:'complete'}); delete legacy.childGenerationVersion; delete legacy.leaseVersion; delete legacy.backupOperationId; delete legacy.leaseUpdatedAt; legacy.backupStartedAt=Timestamp.fromMillis(1); legacy.backupCompletedAt=Timestamp.fromMillis(2); legacy.createdAt=Timestamp.fromMillis(1); legacy.updatedAt=Timestamp.fromMillis(2); await setDoc(doc(c.firestore(),v2()),legacy); });
  const snap=await getDoc(doc(db('a'),v2()));
  await assertSucceeds(setDoc(doc(db('a'),v2()),{...snap.data(),childGenerationVersion:1,leaseVersion:1,backupOperationId:op2,leaseUpdatedAt:serverTimestamp(),backupGeneration:8,backupStatus:'in_progress',backupStartedAt:serverTimestamp(),backupCompletedAt:deleteField(),updatedAt:serverTimestamp()},{merge:true}));
});
test('legacy untagged child can be upgraded to current generation', async()=>{
  const ref=doc(db('a'),v2('/daily/2026-08-03'));
  await env.withSecurityRulesDisabled(async c=>{ const legacy=daily(); delete legacy.backupGeneration; legacy.createdAt=Timestamp.fromMillis(1); legacy.updatedAt=Timestamp.fromMillis(1); await setDoc(doc(c.firestore(),v2('/daily/2026-08-03')),legacy); });
  const snap=await getDoc(ref);
  await assertSucceeds(setDoc(ref,{...daily(),createdAt:snap.data().createdAt,updatedAt:serverTimestamp()}));
});
test('complete transition cannot remove child generation version', async()=>{ const ref=doc(db('a'),v2()); const value=(await getDoc(ref)).data(); delete value.childGenerationVersion; await assertFails(setDoc(ref,{...value,backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });

async function seedRoot(value) {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), v2()), value));
}
const fixedRoot = overrides => ({ ...root(overrides), backupStartedAt: Timestamp.fromMillis(1), leaseUpdatedAt: Timestamp.fromMillis(1), createdAt: Timestamp.fromMillis(1), updatedAt: Timestamp.fromMillis(1) });

test('active lease takeover is denied', async()=>{ const ref=doc(db('a'),v2()); const now=Timestamp.now(); await seedRoot({...fixedRoot(),backupStartedAt:now,leaseUpdatedAt:now}); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:2,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('stale lease takeover is allowed', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); const s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,backupGeneration:2,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('stale takeover requires generation plus one', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:3,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('stale takeover requires new operation id', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('takeover requires server lease timestamp', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:2,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:Timestamp.fromMillis(2),updatedAt:serverTimestamp()})); });
test('current lease heartbeat is allowed', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('heartbeat with old generation is denied', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:0,leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('heartbeat with old operation is denied', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupOperationId:op2,leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('takeover fences old generation child and allows new generation child', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); let s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,backupGeneration:2,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); await assertFails(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily({backupGeneration:1}))); await assertSucceeds(setDoc(doc(db('a'),v2('/daily/2026-08-03')),daily({backupGeneration:2}))); });
test('takeover fences old generation complete', async()=>{ const ref=doc(db('a'),v2()); await seedRoot(fixedRoot()); let s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,backupGeneration:2,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:1,backupOperationId:op1,backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('current generation and operation complete is allowed', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('complete with changed operation is denied', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupOperationId:op2,backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('lease unknown root field is denied', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,leaseOwner:'device',leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('lease version change is denied on complete', async()=>{ const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,leaseVersion:2,backupStatus:'complete',backupCompletedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('legacy stale in progress with valid timestamp can migrate', async()=>{ const legacy=fixedRoot(); delete legacy.leaseVersion; delete legacy.backupOperationId; delete legacy.leaseUpdatedAt; await seedRoot(legacy); const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertSucceeds(setDoc(ref,{...s,backupGeneration:2,leaseVersion:1,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
test('legacy in progress missing timestamp is denied', async()=>{ const legacy=fixedRoot(); delete legacy.leaseVersion; delete legacy.backupOperationId; delete legacy.leaseUpdatedAt; delete legacy.backupStartedAt; await seedRoot(legacy); const ref=doc(db('a'),v2()); const s=(await getDoc(ref)).data(); await assertFails(setDoc(ref,{...s,backupGeneration:2,leaseVersion:1,backupOperationId:op2,backupStartedAt:serverTimestamp(),leaseUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()})); });
