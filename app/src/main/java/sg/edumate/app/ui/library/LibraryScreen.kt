@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edumate.app.data.resource.UserUploadAdapter
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.ui.components.*
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.ResourceRepository
import sg.edumate.domain.usecase.SearchResourcesUseCase
import javax.inject.Inject

data class LibraryUiState(val query: ResourceQuery = ResourceQuery(), val results: List<Resource> = emptyList(), val loading: Boolean = false)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val search: SearchResourcesUseCase,
    private val repo: ResourceRepository,
    private val uploads: UserUploadAdapter,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    private val query = MutableStateFlow(ResourceQuery())
    private val refresh = MutableStateFlow(0)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    init {
        viewModelScope.launch {
            combine(query.debounce(250), refresh) { q, _ -> q }.collectLatest { q ->
                _state.update { it.copy(query = q, loading = true) }
                val r = search(q)
                _state.update { it.copy(results = r, loading = false) }
            }
        }
    }

    fun setText(t: String) { query.update { it.copy(text = t) }; _state.update { it.copy(query = it.query.copy(text = t)) } }
    fun toggleSource(s: ResourceSourceKind) = query.update { it.copy(sources = it.sources.toggle(s)) }
    fun toggleType(t: ResourceType) = query.update { it.copy(types = it.types.toggle(t)) }
    fun toggleStream(s: Stream) = query.update { it.copy(streams = it.streams.toggle(s)) }
    fun toggleBookmarkedOnly() = query.update { it.copy(bookmarkedOnly = !it.bookmarkedOnly) }
    fun bookmark(id: String) = viewModelScope.launch { repo.toggleBookmark(id); refresh.value++ }
    fun offline(r: Resource) = viewModelScope.launch { repo.setOffline(r.id, !r.offlineAvailable); refresh.value++ }
    fun add(title: String, url: String, type: ResourceType) = viewModelScope.launch {
        val p = prefs.prefs.first()
        uploads.add(title, url.ifBlank { null }, type, p.primaryLevel ?: Level.SEC1, p.subjectIds.firstOrNull() ?: "math", null); refresh.value++
    }

    private fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v
}

@Composable
fun LibraryScreen(vm: LibraryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add my resource") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(s.query.text, vm::setText, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search topics, titles…") }, singleLine = true)
            }
            item {
                Column {
                    ChipSelector(ResourceSourceKind.entries, s.query.sources, { it.label }, vm::toggleSource)
                    ChipSelector(ResourceType.entries, s.query.types, { it.label }, vm::toggleType)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Stream.G1, Stream.G2, Stream.G3).forEach { st -> FilterChip(st in s.query.streams, { vm.toggleStream(st) }, label = { Text(st.label) }) }
                        FilterChip(s.query.bookmarkedOnly, vm::toggleBookmarkedOnly, label = { Text("Bookmarked") }, leadingIcon = { Icon(Icons.Outlined.Bookmark, null) })
                    }
                }
            }
            if (s.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!s.loading && s.results.isEmpty()) item { EmptyState("No resources found", "Try fewer filters, or add your own resource.") }
            items(s.results, key = { it.id }) { r -> ResourceCard(r, onBookmark = { vm.bookmark(r.id) }, onOffline = { vm.offline(r) }) }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
    if (adding) AddResourceDialog(onDismiss = { adding = false }, onAdd = { t, u, ty -> vm.add(t, u, ty); adding = false })
}

@Composable
private fun ResourceCard(r: Resource, onBookmark: () -> Unit, onOffline: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onBookmark) {
                    Icon(if (r.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (r.bookmarked) "Remove bookmark" else "Bookmark")
                }
                IconButton(onClick = onOffline) {
                    Icon(if (r.offlineAvailable) Icons.Outlined.OfflinePin else Icons.Outlined.DownloadForOffline,
                        contentDescription = if (r.offlineAvailable) "Remove offline copy" else "Make available offline")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (r.isMock) StatusPill("Sample data", color = MaterialTheme.colorScheme.errorContainer)
                StatusPill(r.type.label); StatusPill(r.level.label)
                r.streams.forEach { StatusPill(it.label) }
            }
            Text("Source: ${r.sourceName} · Licence: ${r.licence.name}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            if (r.licence.requiresAttribution) Text("Attribution: ${r.attribution}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddResourceDialog(onDismiss: () -> Unit, onAdd: (String, String, ResourceType) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ResourceType.SLIDES) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add my resource") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Link (https://, optional)") }, singleLine = true,
                    isError = url.isNotBlank() && !url.startsWith("https://"))
                ChipSelector(ResourceType.entries, setOf(type), { it.label }, { type = it })
                Text("Only add materials you own or are licensed to use.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(title, url, type) }, enabled = title.isNotBlank() && (url.isBlank() || url.startsWith("https://"))) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
