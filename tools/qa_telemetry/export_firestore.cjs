const fs = require('fs');
const path = require('path');
const adminRoot = path.resolve(__dirname, '..', '..', 'functions', 'node_modules', 'firebase-admin');
const {initializeApp, getApps} = require(path.join(adminRoot, 'lib', 'app'));
const {getFirestore} = require(path.join(adminRoot, 'lib', 'firestore'));

const args = process.argv.slice(2);
const outputIndex = args.indexOf('--output');
if (outputIndex < 0 || !args[outputIndex + 1]) throw new Error('--output is required');
const output = path.resolve(args[outputIndex + 1]);
const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || 'steparena-dev';
if (projectId !== 'steparena-dev') throw new Error(`Refusing non-QA project: ${projectId}`);
if (!getApps().length) initializeApp({projectId});

const forbidden = /(?:uid|email|token|secret|credential|password|ssid|bssid|challengeid|access.?key)/i;
function safe(value, key = '') {
  if (forbidden.test(key)) return undefined;
  if (value && typeof value.toDate === 'function') return value.toDate().toISOString();
  if (Array.isArray(value)) return value.map(item => safe(item)).filter(item => item !== undefined);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, safe(v, k)]).filter(([, v]) => v !== undefined));
  if (typeof value === 'string' && /(?:bearer\s+|access_token|refresh_token|AIza[0-9A-Za-z_-]{20,}|@)/i.test(value)) return '[REDACTED]';
  return value;
}

async function readSubcollection(device, name) {
  const collection = device.collection(name);
  const snapshot = await collection.orderBy('serverReceivedAt', 'desc').limit(500).get();
  return snapshot.docs.map(doc => safe({id: doc.id, ...doc.data()}));
}

async function main() {
  const db = getFirestore();
  const devices = {};
  for (const device of await db.collection('qaTelemetryDevices').listDocuments()) {
    const alias = device.id;
    devices[alias] = {
      events: await readSubcollection(device, 'events'),
      snapshots: await readSubcollection(device, 'snapshots'),
    };
  }
  fs.mkdirSync(output, {recursive: true});
  fs.writeFileSync(path.join(output, 'latest.json'), JSON.stringify(safe({generatedAt: new Date().toISOString(), project: 'steparena-dev', devices}), null, 2) + '\n', 'utf8');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
