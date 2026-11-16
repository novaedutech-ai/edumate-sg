# 6 · Privacy and security checklist (mapped to PDPA)

> **Applicability note.** The PDPA applies to private-sector organisations (private schools, international schools, tuition/enrichment centres, and EduMate as a vendor/data intermediary). **Government and government-aided schools act as or on behalf of public agencies**, which are excluded from the PDPA; their obligations come from the Public Sector (Governance) Act and Government Instruction Manuals (e.g. IM8), and they will bind EduMate by contract to equivalent or stricter controls (often including GCC hosting and security assessments). The controls below are designed to satisfy both. Have this mapping reviewed by counsel and the school/MOE data protection office before launch.
>
> Also consider PDPC's *Advisory Guidelines on the Use of Personal Data in AI Recommendation and Decision Systems* and its guidance on children's personal data in the digital environment.

Legend: ✅ implemented in scaffold · 🖥 backend/ops (spec'd) · 📄 process/document

| PDPA obligation | Control | Where | Status |
|---|---|---|---|
| **Consent** | Onboarding requires acknowledging the notice; AI and analytics are separate opt-ins; withdrawal in Privacy & data | `OnboardingScreen`, `PrivacyCenterScreen`, `UserPreferencesRepository` | ✅ |
| | Consent events audit-logged with notice version | `AuditAction.CONSENT_GIVEN` | ✅ |
| | Student/parent consent handled by the school (deemed consent / school notices); app records school's basis in DPR | `templates/data-processing-record.md` | 📄 |
| **Notification** | Versioned in-app privacy notice; re-consent when version bumps | `PrivacyNotice.VERSION` | ✅ |
| **Purpose limitation** | Data used only for teaching workflows; no ads, no sale; model vendor contract: no training on customer data | Notice, vendor DPA | ✅ / 📄 |
| **Access & correction** | Teacher export (JSON) of all on-device data; edit any record; DPO contact for students/parents | `PrivacyDataService.exportJson`, `GET /v1/me/data` | ✅ / 🖥 |
| **Accuracy** | Teachers review/approve all AI output; roster editable; review status tracked | `ReviewStatus`, `SetReviewStatusUseCase` | ✅ |
| **Protection** | TLS 1.2+ only (`ConnectionSpec.RESTRICTED_TLS`), cleartext disabled, certificate pinning slot | `NetworkModule`, `network_security_config.xml` | ✅ (add pins pre-release) |
| | Encryption at rest: SQLCipher DB; Keystore-wrapped secrets; server CMEK | `DatabaseModule`, `SecretStore` | ✅ / 🖥 |
| | App lock (biometric / device PIN) after 60 s background; school can require it | `AppLockManager` | ✅ |
| | `FLAG_SECURE` in release (no screenshots/recents thumbnails); no backups / device transfer | `MainActivity`, `data_extraction_rules.xml` | ✅ |
| | Data minimisation: pseudonyms by default; NRIC/FIN, contacts not modelled; CSV identifier columns rejected; no location/contacts/camera permissions | `StudentRef`, `RosterCsvImporter`, manifest | ✅ |
| | **No identifiable student data to AI:** on-device tokenisation of roster names, NRIC/FIN, email, SG phone; restored only on device; output re-checked for PII | `PiiTokeniser`, `SafeAiGateway` | ✅ (tests) |
| | Content moderation of AI output (blocked) and student submissions (hidden + pastoral flag to teacher) | `LocalContentModerator`, `LiveSessionReducer` | ✅ + 🖥 server model |
| | RBAC (teacher, HOD, admin, student) and school-level tenant isolation (row-level security by `schoolId`) | `Role`, admin panel gating; API `x-rbac` | ✅ client / 🖥 enforce server-side |
| | Logs/analytics contain no personal data; audit log stores counts/IDs only | `AuditEntry`, architecture §Observability | ✅ |
| | Security testing: MASVS L2 review, pen test, dependency scanning in CI | CI, release checklist | 📄 |
| **Retention limitation** | Configurable retention; daily purge worker; unreviewed drafts auto-deleted; live responses 30 d | `ApplyRetentionPolicyUseCase`, `RetentionWorker` | ✅ (+ server job 🖥) |
| | Teacher "delete all" on device + server delete request | `PrivacyDataService.deleteAllLocalData`, `DELETE /v1/me/data` | ✅ / 🖥 |
| **Transfer limitation** | All storage/compute in `asia-southeast1`; model vendor endpoint in-region where available, otherwise contractual safeguards (comparable protection) documented | Architecture, DPR | 🖥 / 📄 |
| **Data breach notification** | Incident runbook: assess within 30 days, notify PDPC **within 3 calendar days** of determining a notifiable breach, notify affected individuals/schools; audit log + request IDs support investigation | Notice text; runbook | 📄 |
| **Accountability** | DPO contact in-app and configurable per school; data-processing record template; DPIA before launch | `PrivacyCenterScreen`, `templates/` | ✅ / 📄 |
| **Data intermediary (vendor) duties** | Process only on school's instructions; protection & retention obligations; sub-processor list | Contract | 📄 |

## MOE ICT / cyber-wellness alignment
- Team-based leaderboards by default; individual leaderboards opt-in per school; no public ranking of individual students.
- No teacher↔student private chat unless the school enables it; announcements are class-wide.
- Pastoral-concern detection routes to the teacher privately and is never shown to classmates or pushed to devices.
- Age-appropriate output enforced by moderation and level-aware prompts; AI content always labelled.

## Release gate (must be ✅ before production)
- [ ] Certificate pins configured (primary + backup) and rotation plan
- [ ] Server-side RBAC + RLS tests per role and tenant
- [ ] Server-side PII scan on every AI request (second line of defence) with alerting
- [ ] Vendor DPA: no retention/training, region, sub-processors
- [ ] DPIA signed off by school/MOE DPO; DPR completed
- [ ] Pen test + OWASP MASVS L2 checklist
- [ ] Breach runbook rehearsed
