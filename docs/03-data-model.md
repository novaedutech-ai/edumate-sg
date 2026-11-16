# 3 · Data model

Classification key (enforced by `@Classified` on domain models, checked in `QualityTest`):
**PUB** public · **INT** internal (school-scoped teacher content) · **PER** personal · **SEN** sensitive (minors' data; never sent identifiable to AI, never in analytics/logs).

## Entity relationships
```mermaid
erDiagram
  SCHOOL ||--o{ TEACHER : employs
  SCHOOL ||--|| SCHOOL_SETTINGS : configures
  SCHOOL ||--o{ CLASS : has
  TEACHER ||--o{ CLASS : teaches
  CLASS ||--o{ STUDENT : enrols
  TEACHER ||--o{ LESSON_PLAN : owns
  LESSON_PLAN ||--o{ LESSON_PLAN : "parentPlanId (versions)"
  LESSON_PLAN }o--|| SYLLABUS_TOPIC : targets
  SYLLABUS_TOPIC ||--o{ LEARNING_OUTCOME : contains
  LESSON_PLAN ||--o{ LESSON_BLOCK : "flow"
  WORKSHEET }o--|| SYLLABUS_TOPIC : targets
  WORKSHEET ||--|{ WORKSHEET_VERSION : "one per stream"
  WORKSHEET_VERSION ||--|{ QUESTION : contains
  CLASS ||--o{ ASSIGNMENT : sets
  ASSIGNMENT }o--o| WORKSHEET : uses
  CLASS ||--o{ ANNOUNCEMENT : posts
  CLASS ||--o{ LIVE_SESSION : runs
  LIVE_SESSION ||--o{ SESSION_PARTICIPANT : "join code"
  SESSION_PARTICIPANT }o--o| STUDENT : "maps to (server only)"
  LIVE_SESSION ||--o{ RESPONSE : collects
  LIVE_SESSION ||--o{ INTERVENTION : sends
  RESOURCE }o--o{ LESSON_PLAN : "cited by (verified)"
  TEACHER ||--o{ AUDIT_ENTRY : generates
  TEACHER ||--o{ CONSENT_RECORD : gives
```

## Entities and per-field classification

### School / settings
| Field | Type | Class | Notes |
|---|---|---|---|
| schoolId | string | INT | Tenant key; every server row is partitioned by it (row-level security) |
| dpoName, dpoEmail, dpoPhone | string | PER | Shown in-app (PDPA accountability) |
| allowRealStudentNames | bool = **false** | INT | Off → pseudonyms only |
| allowDirectStudentMessaging | bool = **false** | INT | |
| allowLeaderboards / leaderboardsTeamOnly | bool = true / **true** | INT | |
| aiFeaturesEnabled, requireAppLock | bool | INT | |
| retention.* (days) | int | INT | live 30 · drafts 90 · roster 180 after class end · audit 730 |
| privacyNoticeVersion | int | INT | Bump forces re-consent |

### Teacher (server)
| Field | Class | Notes |
|---|---|---|
| userId (IdP subject) | PER | |
| displayName, email | PER | From IdP; not stored on device beyond token claims |
| role (TEACHER/HOD/SCHOOL_ADMIN) | INT | From IdP group claims |
| departmentIds | INT | Sharing scope |

### Class — `SchoolClass`
| Field | Class |
|---|---|
| id, schoolId, name, academicYear, endsAtEpochMs | INT |
| level, subjectId | PUB |
| stream | INT |

### Student — `StudentRef`
| Field | Class | Notes |
|---|---|---|
| pseudonymId (`STU-XXXXXX`) | PER | Random; the only ID that leaves the device by default |
| classId | INT | |
| alias ("Student 07") | PER | Default display |
| realName | **SEN** | Null unless school enables; SQLCipher-encrypted; tokenised before AI; never synced to analytics |
| stream | INT | |
| masteryEstimate | **SEN** | Derived; used for mixed-ability grouping |
| *NRIC/FIN, contact details, DOB, address* | — | **Not collected** — CSV columns with these headers are rejected |

### Lesson plan — `LessonPlan`
| Field | Class |
|---|---|
| id, title, streams, durationMin, objectives, priorKnowledge, successCriteria, flow[LessonBlock], differentiation, aflChecks, competencies, tags, version, parentPlanId, sharedWithDepartment, reviewStatus, provenance, updatedAt, syncState | INT |
| level, subjectId, topicId | PUB |
| ownerId | PER |

`LessonBlock`: id, type (EXPLAIN · POLL · SHORT_ANSWER · DRAWING · SORTING · MCQ · REFLECTION · GROUP_TASK · EXIT_TICKET), title, instructions, durationMin, tier?, options[], isCheckForUnderstanding — all INT.

`AiProvenance` (INT): generatedBy, generatedAt, requestId, syllabusOutcomesCited[], citations[] (only verified), citationNote (set when no source), moderationFlags[], isOfflineTemplate.

`ReviewStatus`: DRAFT_AI → TEACHER_EDITED → APPROVED | REJECTED. **Only APPROVED is shareable with students** (`shareableWithStudents`).

### Worksheet — `Worksheet` → `WorksheetVersion` → `WorksheetQuestion`
| Field | Class |
|---|---|
| Worksheet: id, title, objectives, misconceptions, reviewStatus, provenance, updatedAt, syncState | INT |
| Worksheet: topicId, level, subjectId | PUB · ownerId PER |
| Version: stream, questions, answerKey[AnswerKeyItem], checks[QualityCheck] | INT |
| Question: id, number, type, difficulty, prompt, options, scaffolds[Scaffold(kind, content)], marks, outcomeCode, isChallenge | INT |

Coordination invariant (checked): all versions share the same core question count and outcome sequence; extension versions may append challenge items.

### Resource — `Resource`
id, title, summary, sourceKind (MOE_LIBRARY · USER_UPLOAD · OPEN_LICENSED), sourceName, licence(name, url, allowsModification, requiresAttribution), attribution, level, subjectId, topicIds, type, streams, url, **isMock**, bookmarked, offlineAvailable. PUB for MOE/OER; INT for user uploads. `isMock` records are badged in the UI and never passed to the AI as citable references.

### Classroom workflow
| Entity | Fields | Class |
|---|---|---|
| Assignment | id, classId, title, dueEpochMs, rubric, linkedDocUrl, worksheetId, submitted, total, toMark, remindBeforeMinutes | INT |
| Submission (server, v1) | assignmentId, pseudonymId, status, score, feedback, feedbackReviewStatus | **SEN** |
| Announcement | id, classId, audience (STUDENTS/PARENTS), body, scheduledFor, sentAt, readCount, recipientCount | INT (body may be PER) |
| LessonSlot | id, classId, className, startsAt, durationMin, lessonPlanId, room | INT |

### Live session
| Entity | Fields | Class | Retention |
|---|---|---|---|
| LiveSession | sessionId, joinCode, activityTitle, startedAt, classId | INT | analytics 365 d |
| SessionParticipant | session-scoped studentId (`p07`), alias, stream | PER | 30 d |
| Response | studentId, blockId, text, confidence, correct, at | **SEN** | 30 d |
| Flags (incl. PASTORAL_CONCERN) | studentId, reasons | **SEN** | session only on device; pastoral flags are never pushed to student devices |
| Intervention | id, title, content, tier, targetStudentIds, sentAt | INT | 30 d |
| SessionReport | masteryByOutcome, masteryByStream, misconceptionClusters | INT (aggregated, no IDs) | 365 d |

### Privacy & audit
| Entity | Fields | Class | Notes |
|---|---|---|---|
| ConsentRecord | userId, noticeVersion, acceptedAt, aiProcessingAccepted, analyticsAccepted | PER | |
| AuditEntry | id, at, actorRole, action, detail, piiTokensReplaced, requestId | INT | **No prompt or student content**, only counts and IDs |

## On-device storage
| Store | Contents | Protection |
|---|---|---|
| Room (`edumate.db`) | plans, worksheets, classes, students, assignments, announcements, resource flags, user resources, audit log | SQLCipher, 256-bit key wrapped by Keystore AES-GCM |
| `edumate_secure` prefs | DB passphrase, access/refresh tokens | Encrypted with Keystore key |
| DataStore `edumate_prefs` | onboarding, levels/subjects, consent flags, school settings cache | No personal data beyond consent flags |
| Cache `exports/` | generated PDFs / data exports | Shared only via FileProvider grant; cleared by OS |
| Backups | — | Disabled (`allowBackup=false`, data-extraction rules exclude all) |
