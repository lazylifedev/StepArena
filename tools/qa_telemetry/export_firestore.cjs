const fs = require('fs');
const path = require('path');

const adminRoot = path.resolve(__dirname, '..', '..', 'functions', 'node_modules', 'firebase-admin');

const DEVICE_ALIASES = Object.freeze(['POCO_X7_PRO_QA', 'SOV41_QA']);
const FORBIDDEN_KEY = /(?:uid|email|token|secret|credential|password|ssid|bssid|challenge.?id|access[_-]?key|refresh|oauth|latitude|longitude|geohash|location|address)/i;
const FORBIDDEN_VALUE = /(?:bearer\s+|access[_-]?token|refresh[_-]?token|AIza[0-9A-Za-z_-]{20,}|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,})/i;
const DEFAULT_SINCE_HOURS = 168;

function timestampToIso(value) {
  if (value == null) return null;
  if (value instanceof Date) return value.toISOString();
  if (typeof value.toDate === 'function') return value.toDate().toISOString();
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? new Date(parsed).toISOString() : null;
  }
  if (typeof value === 'number' && Number.isFinite(value)) return new Date(value).toISOString();
  return null;
}

function sanitizeValue(value, key = '') {
  if (FORBIDDEN_KEY.test(key)) return undefined;
  if (value && typeof value.toDate === 'function') return value.toDate().toISOString();
  if (value instanceof Date) return value.toISOString();
  if (Array.isArray(value)) return value.map(item => sanitizeValue(item)).filter(item => item !== undefined);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .map(([childKey, childValue]) => [childKey, sanitizeValue(childValue, childKey)])
        .filter(([, childValue]) => childValue !== undefined),
    );
  }
  if (typeof value === 'string') {
    if (FORBIDDEN_VALUE.test(value)) return '[REDACTED]';
    return value.slice(0, 512);
  }
  return value;
}

function sanitizeDocument(data) {
  return sanitizeValue(data) || {};
}

function dataOnly(data, metadataKeys) {
  return Object.fromEntries(Object.entries(data || {}).filter(([key]) => !metadataKeys.has(key)));
}

function recordEvent(doc) {
  const raw = doc.data();
  return {
    type: typeof raw.type === 'string' ? raw.type : 'UNKNOWN',
    event_timestamp: timestampToIso(raw.eventTimestamp),
    server_received_at: timestampToIso(raw.serverReceivedAt),
    data: sanitizeDocument(dataOnly(raw, new Set(['type', 'eventTimestamp', 'serverReceivedAt']))),
  };
}

function recordSnapshot(doc) {
  const raw = doc.data();
  return {
    snapshot_timestamp: timestampToIso(raw.snapshotTimestamp),
    server_received_at: timestampToIso(raw.serverReceivedAt),
    data: sanitizeDocument(dataOnly(raw, new Set(['snapshotTimestamp', 'serverReceivedAt']))),
  };
}

function recordTime(record) {
  return Date.parse(record.server_received_at || record.event_timestamp || record.snapshot_timestamp || '') || 0;
}

async function readSubcollection(deviceRef, name, sinceEpochMillis, formatter) {
  const snapshot = await deviceRef.collection(name).orderBy('serverReceivedAt', 'desc').limit(500).get();
  return snapshot.docs
    .map(formatter)
    .filter(record => recordTime(record) === 0 || recordTime(record) >= sinceEpochMillis)
    .sort((left, right) => recordTime(right) - recordTime(left));
}

async function readDevice(db, alias, sinceEpochMillis) {
  const deviceRef = db.collection('qaTelemetryDevices').doc(alias);
  const [events, snapshots] = await Promise.all([
    readSubcollection(deviceRef, 'events', sinceEpochMillis, recordEvent),
    readSubcollection(deviceRef, 'snapshots', sinceEpochMillis, recordSnapshot),
  ]);
  return {alias, events, snapshots};
}

async function collectTelemetry({db, now = new Date(), sinceHours = DEFAULT_SINCE_HOURS} = {}) {
  const generatedAt = now.toISOString();
  const sinceEpochMillis = now.getTime() - Math.max(1, sinceHours) * 60 * 60 * 1000;
  const devices = {};
  for (const alias of DEVICE_ALIASES) devices[alias] = await readDevice(db, alias, sinceEpochMillis);
  return {
    schema_version: 1,
    generated_at: generatedAt,
    project: 'steparena-dev',
    since_hours: sinceHours,
    devices,
  };
}

function stabilizeGeneratedAt(telemetry, previous) {
  if (
    previous &&
    previous.project === telemetry.project &&
    previous.since_hours === telemetry.since_hours &&
    JSON.stringify(previous.devices) === JSON.stringify(telemetry.devices) &&
    typeof previous.generated_at === 'string'
  ) {
    return {...telemetry, generated_at: previous.generated_at};
  }
  return telemetry;
}

function parseArgs(argv = process.argv.slice(2)) {
  const args = {output: null, sinceHours: DEFAULT_SINCE_HOURS};
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === '--output') args.output = argv[++index];
    else if (argv[index] === '--since-hours') args.sinceHours = Number(argv[++index]);
    else throw new Error(`Unknown argument: ${argv[index]}`);
  }
  if (!args.output) throw new Error('--output is required');
  if (!Number.isFinite(args.sinceHours) || args.sinceHours <= 0 || args.sinceHours > 168) {
    throw new Error('--since-hours must be between 1 and 168');
  }
  return args;
}

async function main(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);
  const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || 'steparena-dev';
  if (projectId !== 'steparena-dev') throw new Error(`Refusing non-QA project: ${projectId}`);
  const {initializeApp, getApps} = require(path.join(adminRoot, 'lib', 'app'));
  const {getFirestore} = require(path.join(adminRoot, 'lib', 'firestore'));
  if (!getApps().length) initializeApp({projectId: 'steparena-dev'});
  const output = path.resolve(args.output);
  const latest = path.join(output, 'latest');
  fs.mkdirSync(latest, {recursive: true});
  const collected = await collectTelemetry({db: getFirestore(), sinceHours: args.sinceHours});
  let previous = null;
  const latestPath = path.join(latest, 'latest.json');
  if (fs.existsSync(latestPath)) {
    try { previous = JSON.parse(fs.readFileSync(latestPath, 'utf8')); } catch { previous = null; }
  }
  const telemetry = stabilizeGeneratedAt(collected, previous);
  fs.writeFileSync(latestPath, JSON.stringify(telemetry, null, 2) + '\n', 'utf8');
}

module.exports = {
  DEVICE_ALIASES,
  FORBIDDEN_KEY,
  sanitizeValue,
  sanitizeDocument,
  timestampToIso,
  collectTelemetry,
  stabilizeGeneratedAt,
  parseArgs,
  main,
};

if (require.main === module) {
  main().catch(error => {
    console.error(`QA telemetry export failed: ${error.message}`);
    process.exitCode = 1;
  });
}
