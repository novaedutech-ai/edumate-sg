# 7 · Phased roadmap

Estimates assume 2 Android, 2 backend, 1 design, 0.5 QA and a part-time DPO/legal reviewer.

## MVP: planner + worksheets (≈ 10–12 weeks)
**Goal:** a teacher can plan and differentiate offline-first, with privacy controls that pass a school DPIA.

| Area | Deliverables | Exit criteria |
|---|---|---|
| App (done in scaffold) | Onboarding + consent, Today, Planner (3-step generate, edit, version, refine, tone), Worksheet generator (coordinated tiers, checks, answer key, PDF), Library (3 adapters, bookmarks, offline flag), Classes (CSV pseudonymised roster, assignments + reminders, announcements), Privacy center | All screens usable with TalkBack and 200 % font; cold start < 2 s on a mid-range device (e.g. Galaxy A3x) |
| Backend | OIDC (Google Workspace for Education + generic IdP), `/v1/ai/lesson-plans`, `/v1/ai/worksheets`, server-side PII re-scan + moderation, plan sync, `/v1/me/data`, audit store; all in `asia-southeast1` | 0 PII hits in AI logs during pilot; p95 generation < 15 s |
| Content | Real syllabus tree for 2–3 pilot subjects/levels (licensed from official sources; replace placeholder codes); prompt templates reviewed by subject specialists | ≥ 70 % of drafts approved after edits |
| Compliance | DPIA, DPR, vendor DPA, privacy notice reviewed by counsel; pen test | DPO sign-off from pilot schools |
| Engineering | CI (domain tests, unit, lint, Compose UI on emulator), baseline profiles, crash reporting without PII, feature flags | Green CI, crash-free sessions ≥ 99.5 % |
| Pilot | 2–3 schools, ~30 teachers | Weekly active ≥ 60 % of pilot teachers |

## v1: live sessions + analytics (≈ +10 weeks)
- Production WebSocket live service (45 students/session, < 1 s p95) and the **student web join client** (no account; join code; accessible, low-bandwidth).
- Lesson-timeline runner: teacher advances blocks; polls, MCQ, short answer, drawing/annotation boards, sorting, confidence slider, exit tickets.
- Shared group boards for auto-grouped teams.
- AI-suggested feedback on open responses with approve / edit / reject queue before release.
- Post-session analytics UI: mastery by outcome, misconception clusters, per-stream comparison; "reteach" suggestions feeding back into the planner.
- Google Docs/Slides export and per-lesson Drive folder ("paperless hub"); DOCX export.
- Department sharing on the server, HOD review queue, plan comments.
- Drag-to-reorder in the lesson timeline (in addition to accessible move buttons).

## v2: MOE integration, gamification, admin console (≈ +12 weeks)
- **MOE integration via official channels only** (subject to MOE approval): resource API with school SSO behind `ResourceAdapter`; explore SLS interoperability; SEAB syllabus references for exam levels.
- Gamified review: team leaderboards (default), opt-in individual boards, streaks, timers, power-ups, "review missed questions" mode, controlled by school policy.
- Web **school admin console**: user/role management, retention policies, DPO request workflow (student data access/deletion by pseudonym), audit search, policy templates aligned to MOE ICT and cyber-wellness guidelines.
- Timetable integration for the Today dashboard; parent channel via school-approved messaging.
- Multi-language support for Mother Tongue subjects (Chinese, Malay, Tamil) in generation and UI.
- Tablet-optimised two-pane editor (list/detail) and stylus annotation.

## Risks and mitigations
| Risk | Mitigation |
|---|---|
| AI quality or alignment varies by subject | Subject-specialist prompt reviews, outcome-code grounding, teacher approval gate, quality checks |
| Government-school hosting/security requirements (e.g. GCC, IM8) | Decide deployment model early (open question 1); keep infra in Terraform so it can move |
| Syllabus content licensing | Use official, licensed sources only; placeholders until agreements are in place |
| Live-session scale in poor Wi-Fi classrooms | Small frames, reconnect with resume token, HTTP fallback for submissions |
| Over-tokenisation hides useful context | Teachers see restored text; tokeniser tuned with pilot data; false positives are acceptable by design |
