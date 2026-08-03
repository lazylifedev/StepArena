# Cloud Firestore backup

The app writes a private, one-way backup under `userBackups/{uid}`. Room remains the local source of truth; restore and bidirectional synchronization are intentionally not implemented.

Run the Security Rules suite from the repository root:

```powershell
cd firebase-rules-tests
npm install
npx firebase-tools@14.22.0 emulators:exec --project steparena-rules-test --only firestore "npm test"
```

Deploy rules only after the suite passes, and only to the `qa` alias:

```powershell
npx firebase-tools@14.22.0 deploy --only firestore:rules --project qa
```

Do not deploy these rules to the `production` alias in this phase. Emulator artifacts, logs, and `node_modules` are ignored and must not be committed.
