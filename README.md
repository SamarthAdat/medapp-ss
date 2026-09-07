# MedRecord Keeper

An offline-first Android app for keeping medical records for several people —
yourself, family, dependents — under one account. Clinic and hospital visits,
report files, medicine and next-visit reminders, and nearby facility search,
all working fully offline and syncing to Firebase in the background.

Built to support HIPAA and India's DPDP Act 2023 / ABDM: consent before any
processing, encrypted local storage, per-user and per-patient access scoping,
and an immutable audit trail.

## Status

Built phase by phase. Each phase is verified on a device — installed, driven
through the real flows, and checked against the encrypted database — before it
is committed.

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Build toolchain, DI, navigation foundation | Done |
| 1 | Auth, versioned consent gate, encrypted local database | Done |
| 2 | Patient management, active-patient context | Done |
| 3 | Sync engine, outbox, audit logging | Done |
| 4 | Facilities and visit records | Done |
| 5 | Reports and the file pipeline (2 MB cap) | Done |
| 6 | Medicines, reminders, notifications | Done |
| 7 | Dashboard and aggregated views | Done |
| 8 | Maps and nearby facilities | Planned |
| 9 | Settings and compliance hardening | Planned |
| 10 | Polish, QA, release | Planned |

Phase 5 needs the Firebase project on the Blaze plan, because Cloud Storage
buckets require it on projects created after late 2024, and it needs the default
Storage bucket provisioned (Firebase console → Storage → Get started). Blaze
includes the same free quotas as Spark, so development volume costs nothing.
Until the bucket exists, report files queue locally exactly as they do offline.

## Architecture

MVVM with a repository layer, in a single Gradle module organised by concern:

```
core/      cross-cutting: errors, results, dispatchers, connectivity, UI contracts
domain/    models, repository interfaces, session managers, sync contracts, validation
data/      Room entities and DAOs, Firestore data sources, repository impls, syncers
ui/        Compose screens, one contract per feature, navigation
di/        Hilt modules
```

Every feature follows the same contract: one immutable `UiState`, one sealed
`UiEvent` funnelled through `onEvent`, one sealed `UiEffect` channel for
one-shot navigation and messages. Screens are stateless and previewable; a thin
`XRoute` composable owns the ViewModel and translates effects into navigation.

**Offline-first.** Reads observe Room and never wait on the network. Writes land
in Room first and are pushed to Firestore best-effort; a failed push leaves the
row `PENDING` for the sync worker to retry. This holds for server rejections as
well as lost connectivity — a record refused by the security rules is queued and
retried, not lost, and the user sees no error.

**Aggregates are computed once, not per section.** The dashboard and the
timeline observe the whole account and scope in memory rather than issuing a
query per section per patient. At one family's scale that is a handful of small
queries instead of a dozen, and it removes a class of bug the per-section
approach invites: a count claiming three visits beside a list showing two,
because the two reads happened at different moments during a sync. The merging
and date arithmetic live in `domain/usecase` as pure functions, so they are
tested without a database, a dispatcher or Android.

**Reminders are the one thing that never syncs.** Every other record round-trips
to Firestore; reminder rows stay on the device that has to ring. They are a
derivation, not a record — each one is recomputed from a medicine's schedule or
a visit's next-visit date, both of which do sync — and the alarm has to be armed
on this device's `AlarmManager` regardless. Syncing them would add an outbox and
a conflict path for rows the user never edits, and would let a dose reminder
fail to fire because its row had not arrived yet. The visible consequence is
that two devices on one account both notify; for medication that is the better
failure.

One alarm is armed at a time — the next reminder due — and the receiver posts
everything due at that moment before arming the next. Exact alarms are rationed,
and a family with several medicines would otherwise hold a hundred of them for a
week's schedule. A dropped alarm is repaired by the boot receiver and a
twice-daily sweep, both of which re-arm from the table.

**Sync.** `EntitySyncer` implementations are contributed into a Hilt set, so
`SyncWorker` knows nothing about any particular record type; adding one is a
binding, not a change to the worker. Push always runs before pull, since the
reverse order would let a remote copy overwrite a local edit that had not been
sent yet. Syncers run in dependency order — users before patients, because the
rules read the user document for a consent version; audit entries last, so an
entry follows the record it describes. Three triggers: periodic, on reconnect,
and on app foreground.

**Report files** travel separately from their records, because they fail
separately. The metadata row syncs through the ordinary outbox; the bytes are
encrypted on the device and sent by their own worker under its own constraints,
so a stalled 2 MB upload never holds up the audit trail and a failed upload
never delays the record of what was attached. An interrupted upload resumes by
construction rather than by bookkeeping: the encrypted local copy *is* the
queue, and a row only leaves the queue once its bytes are acknowledged.

**Conflicts** are detected, not guessed at. An unpushed local edit that the
server has moved past is flagged `CONFLICT` and left untouched for the Phase 9
resolution screen. Everything else is last-write-wins on `updatedAt`.

**Session-driven navigation.** Which part of the app a user can reach is decided
entirely by `AuthSession`. An account with missing or stale consent resolves to
`PendingConsent`, and the navigation graph has no path from there into any data
screen — so consent-before-processing is structural, not a check a future screen
can forget.

**Parent rows are updated, never replaced.** Room's `@Insert(REPLACE)` is a
delete followed by an insert, and the delete fires `ON DELETE CASCADE` - so
re-applying a patient during an ordinary sync silently destroyed every visit and
report beneath it, including records that had never reached Firestore and so
could not be pulled back. Every upsert uses Room's `@Upsert`, which updates in
place. `CascadeSafetyTest` pins this.

**Dates** that name a calendar day — date of birth, visit date, next visit — are
stored as epoch days rather than timestamps. A birth date has no time or zone,
and storing millis shifts it by a day whenever the device crosses one. Core
library desugaring is enabled so `java.time` is available at minSdk 24.

## Security

- The Room database is encrypted with SQLCipher. The passphrase is generated
  once and sealed with a hardware-backed AES-GCM key in the Android Keystore;
  only the wrapped blob is stored. Verified after each phase: the database file
  and its WAL contain no plaintext names, clinical fields, or even table names.
- Report files are encrypted the same way, with a per-file random key sealed by
  a Keystore key. Envelope encryption rather than encrypting the file directly:
  a Keystore cipher streams every byte across a Binder boundary, which is slow
  at 2 MB and crashed the process outright on a real device. The Keystore key
  only ever sees 32 bytes.
- Plaintext report bytes stay in memory. Uploads and downloads pass byte arrays
  rather than file handles, and the one API that insists on a real file - the
  PDF renderer, which seeks - gets a file that is unlinked the instant it is
  opened, so the decrypted document exists under a name for a few milliseconds
  and is reclaimed even if the process dies mid-render.
- The 2 MB cap is enforced twice: in the app, where it can explain itself and
  offer compression, and in the Storage rules, where it actually holds. Images
  over the cap are recompressed; PDFs are refused rather than rasterised, since
  silently turning a lab result into a JPEG of a lab result is worse than saying
  no. Rules live in `firebase/storage.rules`.
- File pickers are system pickers. The app never requests READ_MEDIA_IMAGES or
  CAMERA: the user chooses one file and the app receives a grant for that file
  alone, so an app holding medical records never gains standing access to the
  photo library or the camera. Reports are viewed in-app rather than handed to
  another viewer through a share intent.
- Firestore's own disk cache is disabled in favour of a memory-only cache. That
  cache is unencrypted SQLite, so leaving it on would put patient data on disk
  in the clear. The encrypted Room database is the only durable local store.
- Firestore rules scope every document by `userId` and `patientId`, check
  ownership from the payload as well as the path, and require a recorded consent
  version before any patient, facility or visit write — so consent-first is
  enforced server-side, not only by the client's session gate. Rules live in
  `firebase/firestore.rules`.
- Consent records and audit entries are append-only: the rules permit `create`
  and refuse `update` and `delete` outright, so a client credential cannot
  rewrite history.
- Audit entries record *that* a record was touched, never its contents. Device
  identity is a hashed per-install random value, never the Android ID, which is
  shared across apps and effectively tracks the person.
- Deletion is a soft delete with a 30-day grace period, satisfying both the
  right to erasure and audit-trail retention.
- Sign-out clears local records and the remembered patient selection. Audit
  entries that have not reached Firestore yet are kept — they are evidence, not
  cache — and stay scoped to their own account by every query.
- Password reset reports success for unknown addresses, so the form cannot be
  used to enumerate who has an account with a health service.
- `android:allowBackup="false"` keeps health data out of device backups.

## Building

Requires JDK 17+ and Android SDK 37.

This repository does not include `google-services.json` — it is per-project
configuration and is gitignored. To build:

1. Create a Firebase project with **Email/Password** authentication enabled and
   a Firestore database. For India data residency, use the `asia-south1`
   (Mumbai) region; this cannot be changed after creation.
2. Register an Android app with the package name `com.ss.medrecord` and download
   `google-services.json` into `app/`.
3. Provision Cloud Storage (Firebase console → Storage → Get started). This
   needs the Blaze plan; see Status above.
4. Deploy the security rules. Both files are one unit - a report is a Firestore
   document plus a Storage object, and deploying one without the other leaves
   the other half unreachable or unwritable:
   ```
   firebase login
   firebase deploy --only firestore:rules,storage
   ```
5. Build and install:
   ```
   ./gradlew :app:installDebug
   ```

Run the tests with `./gradlew :app:testDebugUnitTest`. The cascade guards, the
reminder engine, the joined queries and the dashboard's Compose rendering need a
device or emulator: `./gradlew :app:connectedDebugAndroidTest`.

**Redeploy the rules whenever a phase adds a collection.** Until they are
published, writes to the new collection are rejected and queue locally. Nothing
is lost, but nothing reaches Firestore either — and the app looks like it is
working, because that is exactly how it is designed to behave offline. Phases 5
and 6 each add collections; 7 and 8 do not.

Reminders need two permissions and degrade rather than fail without either.
`POST_NOTIFICATIONS` is requested at runtime from the medicines screen; refusing
it costs the notifications and nothing else. `SCHEDULE_EXACT_ALARM` is
user-revocable from API 31, and without it alarms are set inexact but
doze-tolerant, so a dose reminder arrives late rather than never. The medicines
screen says which of the two is missing and links to the place to grant it.

Google Maps and Places API keys are needed from Phase 8. Keep the key out of
version control and restrict it to the app's package name and signing
certificate in the Cloud console.

## Database

Room schemas are exported to `app/schemas` so every version bump is diffable in
review. Migrations are hand-written and checked against the generated schema;
destructive fallback is deliberately not enabled, because the local database is
the source of truth for anything not yet synced.

| Version | Adds |
|---------|------|
| 1 | `users`, `consents` |
| 2 | `patients` |
| 3 | `audit_logs` |
| 4 | `facilities`, `visits` |
| 5 | `reports` |
| 6 | `medicines`, `reminders` |

## Compliance note

This codebase implements the app-side technical controls that support HIPAA and
DPDP compliance. Actual compliance also requires organisational measures — a
signed BAA with Google, a published privacy policy, a designated grievance
contact, incident response, and legal review — which are outside the scope of
the code.

Two things need attention before production use with real patient data: the
consent text in `ConsentTexts.kt` is a working draft that needs legal review,
and the grievance contact it names is a placeholder rather than a designated
one, which the DPDP Act requires.
