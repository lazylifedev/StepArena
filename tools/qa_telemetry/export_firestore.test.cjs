const test = require('node:test');
const assert = require('node:assert/strict');
const {DEVICE_ALIASES, sanitizeDocument, sanitizeValue, parseArgs, stabilizeGeneratedAt} = require('./export_firestore.cjs');

test('sanitization removes raw identity, challenge, token, account, wifi, and location data', () => {
  const rawUid = 'raw-user-uid-123';
  const rawChallenge = 'raw-challenge-id-456';
  const rawToken = 'Bearer raw-token-789';
  const result = sanitizeDocument({
    uid: rawUid,
    challengeId: rawChallenge,
    accessToken: rawToken,
    email: 'qa@example.com',
    ssid: 'private-wifi',
    latitude: 35.1,
    safe: 'kept',
    nested: {refresh_token: rawToken, safe: 'nested'},
  });
  const serialized = JSON.stringify(result);
  assert.equal(serialized.includes(rawUid), false);
  assert.equal(serialized.includes(rawChallenge), false);
  assert.equal(serialized.includes(rawToken), false);
  assert.equal(serialized.includes('private-wifi'), false);
  assert.equal(serialized.includes('qa@example.com'), false);
  assert.equal(result.safe, 'kept');
  assert.equal(result.nested.safe, 'nested');
});

test('empty collection and fixed aliases are safe', () => {
  assert.deepEqual(DEVICE_ALIASES, ['POCO_X7_PRO_QA', 'SOV41_QA']);
  assert.deepEqual(sanitizeDocument({}), {});
  assert.equal(sanitizeValue('short value'), 'short value');
});

test('export argument guard rejects invalid windows', () => {
  assert.deepEqual(parseArgs(['--output', 'out']), {output: 'out', sinceHours: 168});
  assert.throws(() => parseArgs(['--output', 'out', '--since-hours', '0']), /between 1 and 168/);
  assert.throws(() => parseArgs(['--output', 'out', '--unknown']), /Unknown argument/);
});

test('unchanged telemetry keeps the previous generated_at for no-change commits', () => {
  const devices = {SOV41_QA: {events: [], snapshots: []}, POCO_X7_PRO_QA: {events: [], snapshots: []}};
  const previous = {project: 'steparena-dev', since_hours: 168, generated_at: '2026-08-08T00:00:00.000Z', devices};
  const current = {project: 'steparena-dev', since_hours: 168, generated_at: '2026-08-09T00:00:00.000Z', devices};
  assert.equal(stabilizeGeneratedAt(current, previous).generated_at, previous.generated_at);
});
