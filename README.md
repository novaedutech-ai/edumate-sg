# EduMate SG (working name)

AI teaching assistant for Singapore educators: syllabus-aligned lesson planning, G1/G2/G3-differentiated worksheets, live checks for understanding with targeted intervention, and daily class workflows. Built privacy-first for minors' data.

| Deliverable | Where |
|---|---|
| 1 · Product requirements & user flows | [`docs/01-product-requirements.md`](docs/01-product-requirements.md) |
| 2 · Architecture & module breakdown | [`docs/02-architecture.md`](docs/02-architecture.md) |
| 3 · Data model with per-field privacy classification | [`docs/03-data-model.md`](docs/03-data-model.md) |
| 4 · Android project scaffold | `core/domain`, `app` (below) |
| 5 · Backend API spec (OpenAPI 3.1) | [`docs/05-api/openapi.yaml`](docs/05-api/openapi.yaml) |
| 6 · Privacy & security checklist (PDPA) | [`docs/06-privacy-security-checklist.md`](docs/06-privacy-security-checklist.md) · [DPR template](docs/templates/data-processing-record.md) |
| 7 · Phased roadmap | [`docs/07-roadmap.md`](docs/07-roadmap.md) |
| Running the AI backend on your PC | [`docs/08-local-backend-setup.md`](docs/08-local-backend-setup.md) |

## Project layout
```
core/domain/   Pure Kotlin. Models, repository interfaces, use cases, SafeAiGateway, PiiTokeniser,
               moderation, worksheet quality checks, live-session reducer, grouping, analytics. 31 tests.
backend/       Kotlin/Ktor AI backend: holds the Claude API key, calls Claude with structured outputs,
               re-checks PII, moderates output, filters citations. 10 tests.
app/           Android app (Compose, Material 3, Hilt, Room+SQLCipher, Retrofit, WorkManager, DataStore).
  data/        Repositories, MOE-mock / upload / OER adapters, Keystore, remote API, live transports, PDF export
  ui/          Today · Plan (planner, lesson editor, worksheet generator) · Library · Live · Classes · Privacy · Onboarding
  work/        Sync, assignment reminders, retention purge
docs/          Deliverables 1–3, 5–7
.github/       CI: domain tests → build/lint/unit → Compose UI tests on emulator
```

## Build & run
Requires Android Studio (Ladybug or newer) / Android SDK 35, JDK 17+.
```bash
./gradlew :core:domain:test          # JVM-only, fast
./gradlew :app:installDebug          # dev build: on-device template AI + simulated live class
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
**Real AI generation** needs the backend running on your PC: see [`docs/08-local-backend-setup.md`](docs/08-local-backend-setup.md) (`.\gradlew :backend:run` with your Claude API key in `backend/secrets.properties`). Debug builds call it at `http://10.0.2.2:8080/` (the emulator's address for your PC). If it isn't running, or the teacher has AI turned off, the app uses `OfflineTemplateProvider` (structured skeletons, clearly labelled). Live sessions use `SimulatedLiveSessionGateway` (32 simulated students) until the live service exists.

## What's mocked or placeholder (by design)
- **Syllabus codes** (`P4-MA-FR`, `S1-MA-AE`, …) are internal placeholders flagged `isPlaceholderCode`; replace with the official syllabus feed.
- **MOE library** returns labelled sample records without URLs; the adapter is shaped for an official API + SSO. Nothing scrapes MOE or any third-party site.
- **Open-licensed** items are sample records; sample (`isMock`) resources are badged and never offered to the AI as citable sources.
- **Auth** uses `DevCurrentUser`; production uses OIDC (AppAuth + PKCE) behind `CurrentUser`.
- **Google Docs/Slides/DOCX export** is spec'd (`POST /v1/exports`); PDF export works on-device.

## Privacy guarantees enforced in code
- `SafeAiGateway` is the only route to AI: tokenises roster names, NRIC/FIN, emails and SG phone numbers on-device; moderates all output; rebuilds citations from teacher-selected resources only (others dropped and audited, with an explicit "no source" note); labels provenance; audit-logs counts, never content.
- AI output is stored as `DRAFT_AI`; only `APPROVED` content can be exported or taught live.
- Pseudonymous rosters by default; CSV columns like NRIC/email/phone are rejected.
- SQLCipher database with a Keystore-wrapped key; biometric/PIN app lock; `FLAG_SECURE`; backups disabled; TLS 1.2+ only.
- Retention purge worker, data export and delete-all, DPO contact, versioned privacy notice with consent.

## Verification status
- `:backend`: **compiled and all 10 tests pass** against a fake model (schema generation, PII rejection, moderation, citation filtering, worksheet coordination, HTTP endpoints, dev token). Not yet tested against the live Claude API from the authoring environment (no key there).
- `:core:domain`: **compiled and all 32 tests pass** (PII tokenisation, gateway safety/citation rules, use cases, coordinated worksheets, roster import, live reducer, grouping, interventions, analytics, privacy-annotation coverage).
- `:app`: written against AGP 8.7 / Compose BOM 2024.12 but **not yet built with the Android toolchain**. The authoring environment had no Android SDK or Google Maven access. All app sources were type-checked with the Kotlin 2.0.21 compiler (serialization and Compose plugins on) against desktop Compose/Material 3 jars, `android-all`, and stubs for the Google-only libraries. That pass found and fixed one bug. Screens, ViewModels, data layer and unit tests type-check cleanly. Not yet checked: the DataStore, WorkManager, Biometric and Activity wiring, KSP (Room/Hilt) code generation, and resource linking. Run `./gradlew :app:assembleDebug` first and expect minor fixes.
