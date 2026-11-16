package sg.edumate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import sg.edumate.app.ui.components.AiProvenanceCard
import sg.edumate.app.ui.theme.EduMateTheme
import sg.edumate.domain.ai.SafeAiGateway
import sg.edumate.domain.model.AiProvenance
import sg.edumate.domain.model.ReviewStatus

class AiProvenanceCardTest {
    @get:Rule val compose = createComposeRule()

    private val provenance = AiProvenance("edumate-backend-v1", 0, "r1", listOf("S1-MA-AE.1"), emptyList(), SafeAiGateway.NO_SOURCE_NOTE)

    @Test fun draftIsLabelledAndShowsSourcesOnTap() {
        compose.setContent { EduMateTheme { AiProvenanceCard(provenance, ReviewStatus.DRAFT_AI) } }
        compose.onNodeWithText("AI draft · not yet reviewed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show details").performClick()
        compose.onNodeWithText("Syllabus outcomes: S1-MA-AE.1").assertIsDisplayed()
        compose.onNodeWithText("Sources: ${SafeAiGateway.NO_SOURCE_NOTE}").assertIsDisplayed()
    }

    @Test fun approvedLabel() {
        compose.setContent { EduMateTheme { AiProvenanceCard(provenance, ReviewStatus.APPROVED) } }
        compose.onNodeWithText("AI-assisted · approved by teacher").assertIsDisplayed()
    }

    @Test fun supportsLargeFontScale() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density = 2f, fontScale = 2f),
            ) { EduMateTheme { AiProvenanceCard(provenance, ReviewStatus.DRAFT_AI) } }
        }
        compose.onNodeWithText("AI draft · not yet reviewed").assertIsDisplayed()
    }
}
