@file:OptIn(ExperimentalLayoutApi::class)

package sg.edumate.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sg.edumate.domain.model.Level
import sg.edumate.domain.model.Subject
import sg.edumate.domain.model.SyllabusTopic

/** Step 1 of generation: level → subject → topic, preset from the teacher's onboarding choices. */
@Composable
fun TopicPicker(
    levels: List<Level>, level: Level?, onLevel: (Level) -> Unit,
    subjects: List<Subject>, subjectId: String?, onSubject: (String) -> Unit,
    topics: List<SyllabusTopic>, topicId: String?, onTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Level", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { l -> FilterChip(selected = l == level, onClick = { onLevel(l) }, label = { Text(l.label) }) }
        }
        Text("Subject", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> FilterChip(selected = s.id == subjectId, onClick = { onSubject(s.id) }, label = { Text(s.name) }) }
        }
        Text("Topic", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        if (topics.isEmpty()) {
            Text("No topics for this level and subject yet.", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { t ->
                FilterChip(selected = t.id == topicId, onClick = { onTopic(t.id) },
                    label = { Text(t.title) },
                    leadingIcon = null)
            }
        }
        topics.firstOrNull { it.id == topicId }?.let { t ->
            Text("${t.code}${if (t.isPlaceholderCode) " (placeholder code)" else ""} · ${t.strand}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.outcomes.forEach { o -> Text("• ${o.description}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
