# MedRecord Keeper

An offline-first Android app for keeping medical records for several people —
yourself, family, dependents — under one account. Clinic and hospital visits,
report files, medicine and next-visit reminders, and nearby facility search,
all working fully offline and syncing to Firebase in the background.

Built to support HIPAA and India's DPDP Act 2023 / ABDM: consent before any
processing, encrypted local storage, per-user and per-patient access scoping,
and an immutable audit trail.

## Status

Built phase by phase. Each phase is verified on a device before it is committed.

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Build toolchain, DI, navigation foundation | Done |
| 1 | Auth, versioned consent gate, encrypted local database | Done |
| 2 | Patient management, active-patient context | Done |
| 3 | Sync engine, outbox, audit logging | Next |
| 4 | Facilities and visit records | Planned |
| 5 | Reports and the file pipeline (2 MB cap) | Planned |
| 6 | Medicines, reminders, notifications | Planned |
| 7 | Dashboard and aggregated views | Planned |
| 8 | Maps and nearby facilities | Planned |
| 9 | Settings and compliance hardening | Planned |
| 10 | Polish, QA, release | Planned |

## Architecture

MVVM with a repository layer, in a single Gradle module organised by concern:

```
core/      cross-cutting: errors, results, dispatchers, connectivity, UI contracts
domain/    models, repository interfaces, session managers, validation
data/      Room entities and DAOs, Firestore data sources, repository impls
ui/        Compose screens, one contract per feature, navigation
di/         Hilt modules
```

Every feature follows the same contract: one immutable `UiState`, one sealed
`UiEvent` funnelled through `onEvent`, one sealed `UiEffect` channel for
one-shot navigation and messages. Screens are stateless and previewable; a thin
`XRoute` composable owns the ViewModel and translates effects into navigation.

**Offline-first.** Reads observe Room and never wait on the network. Writes land
in Room first and are pushed to Firestore best-effort; a failed push leaves the
row `PENDING` for the sync worker to retry. Conflicts resolve last-write-wins on
`updatedAt`, with a manual resolution screen as the fallback.

**Session-driven navigation.** Which part of the app a user can reach is decided
entirely by `AuthSession`. An account with missing or stale consent resolves to
`PendingConsent`, and the navigation graph has no path from there into any data
screen — so consent-before-processing is structural, not a check a future screen
can forget.

## Security

- The Room database is encrypted with SQLCipher. The passphrase is generated
  once and sealed with a hardware-backed AES-GCM key in the Android Keystore;
  only the wrapped blob is stored. Verified: the database file and its WAL
  contain no plaintext names, clinical fields, or even table names.
- Firestore's own disk cache is disabled in favour of a memory-only cache. That
  cache is unencrypted SQLite, so leaving it on would put patient data on disk
  in the clear. The encrypted Room database is the only durable local store.
- Firestore rules scope every document by `userId` and `patientId`, check
  ownership from the payload as well as the path, and require a recorded consent
  version before any patient write. Consent records are append-only. Rules live
  in `firebase/firestore.rules`.
- Deletion is a soft delete with a 30-day grace period, satisfying both the
  right to erasure and audit-trail retention.
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
3. Deploy the security rules:
   ```
   firebase deploy --only firestore:rules
   ```
4. Build and install:
   ```
   ./gradlew :app:installDebug
   ```

Run the tests with `./gradlew :app:testDebugUnitTest`.

Cloud Storage for Firebase is needed from Phase 5 for report uploads, which
requires the Blaze plan on projects created after late 2024. Phases 0–4 run on
the free Spark plan. Google Maps and Places API keys are needed from Phase 8.

## Compliance note

This codebase implements the app-side technical controls that support HIPAA and
DPDP compliance. Actual compliance also requires organisational measures — a
signed BAA with Google, a published privacy policy, a designated grievance
contact, incident response, and legal review — which are outside the scope of
the code. The consent text in `ConsentTexts.kt` is a working draft and needs
legal review before production use with real patient data.
