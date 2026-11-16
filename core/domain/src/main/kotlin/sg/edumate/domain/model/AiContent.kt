package sg.edumate.domain.model

import kotlinx.serialization.Serializable

/** Every AI-produced artefact starts at DRAFT_AI and cannot be shared with students until APPROVED. */
@Serializable
enum class ReviewStatus { DRAFT_AI, TEACHER_EDITED, APPROVED, REJECTED;
    val shareableWithStudents: Boolean get() = this == APPROVED
}

@Serializable
enum class SyncState { LOCAL_ONLY, PENDING_UPLOAD, SYNCED, CONFLICT }

@Serializable
enum class Tone { WARM, NEUTRAL, FORMAL, PLAYFUL }

@Serializable
enum class Refinement(val label: String) {
    MAKE_EASIER("Make easier"),
    ADD_EXTENSION("Add extension"),
    MORE_SG_CONTEXT("More Singapore context"),
    SHORTER("Shorter"),
    MORE_INTERACTIVE("More interactive"),
}

/** Only resources the teacher explicitly selected may be cited. See SafeAiGateway. */
@Serializable
data class Citation(val resourceId: String, val title: String, val source: String, val licence: String)

@Serializable
data class AiProvenance(
    val generatedBy: String,
    val generatedAtEpochMs: Long,
    val requestId: String,
    val syllabusOutcomesCited: List<String>,
    val citations: List<Citation>,
    /** Shown when [citations] is empty, e.g. "No external source used". Never left blank. */
    val citationNote: String?,
    val moderationFlags: List<String> = emptyList(),
    val isOfflineTemplate: Boolean = false,
)
