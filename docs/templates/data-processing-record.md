# Data processing record — EduMate SG

*Complete one per school deployment. Owner: school DPO. Review annually or on material change.*

| Item | Entry |
|---|---|
| Organisation (data controller) | `<school name>` |
| Data intermediary | EduMate SG `<legal entity, UEN>` |
| DPO contact (school / vendor) | `<name, email, phone>` / `<name, email>` |
| Date / version | `<yyyy-mm-dd>` / `<v>` |

## Processing activities

| # | Activity | Data subjects | Personal data categories | Purpose | Legal basis / consent approach | Source | Recipients / sub-processors | Location | Retention | Safeguards |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Teacher accounts | Teachers | Name, school email, role | Sign-in, access control | Employment / consent | School IdP | Hosting provider | SG | Account life + 90 d | OIDC, RBAC |
| 2 | Class rosters | Students | Pseudonym, optional name, stream | Class management, grouping | School notice to parents | Teacher CSV | — | Device + SG | 180 d after class ends | Pseudonymisation, SQLCipher |
| 3 | Live responses | Students | Answers, confidence, status | Formative assessment | School notice | Students | — | SG | 30 d | Session-scoped IDs, moderation |
| 4 | AI generation | Teachers (indirectly students) | Tokenised teacher notes / responses | Plan, worksheet, feedback drafting | Consent (AI opt-in) | Teacher | `<model vendor>` | `<region>` | Not retained by vendor | On-device tokenisation, audit log |
| 5 | Analytics (optional) | Teachers | Feature usage counts | Product improvement | Consent (opt-in) | App | `<analytics vendor>` | `<region>` | 14 months | No PII, aggregated |
| 6 | Announcements | Students, parents | Message content, read status | Class communication | School notice | Teacher | Push provider | SG | 1 year | No DMs by default |

## Transfers outside Singapore
`<list any; describe comparable-protection mechanism>`

## Risk assessment summary (DPIA reference)
`<link / summary of residual risks and mitigations>`

## Breach response
Assess within 30 days of awareness · notify PDPC within 3 calendar days once notifiable · notify affected individuals · log in incident register `<link>`.

## Sign-off
School DPO: ________ Date: ____  Vendor DPO: ________ Date: ____
