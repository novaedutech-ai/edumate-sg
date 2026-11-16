package sg.edumate.app.data.resource

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import sg.edumate.app.data.local.ResourceDao
import sg.edumate.app.data.local.ResourceFlagEntity
import sg.edumate.domain.model.Resource
import sg.edumate.domain.model.ResourceQuery
import sg.edumate.domain.repository.ResourceAdapter
import sg.edumate.domain.repository.ResourceRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Fans out to every adapter, merges, and overlays local bookmark/offline flags. */
@Singleton
class ResourceRepositoryImpl @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards ResourceAdapter>,
    private val dao: ResourceDao,
    private val json: Json,
) : ResourceRepository {

    override suspend fun search(query: ResourceQuery): List<Resource> = coroutineScope {
        val flags = dao.flags().associateBy { it.resourceId }
        val adapterQuery = query.copy(bookmarkedOnly = false)
        val results = adapters.filter { query.sources.isEmpty() || it.kind in query.sources }
            .map { a -> async { runCatching { a.search(adapterQuery) }.getOrElse { emptyList() } } }
            .awaitAll().flatten()
            .map { r -> flags[r.id]?.let { r.copy(bookmarked = it.bookmarked, offlineAvailable = it.offline) } ?: r }
        results.filter { !query.bookmarkedOnly || it.bookmarked }.sortedWith(compareByDescending<Resource> { it.bookmarked }.thenBy { it.title })
    }

    override suspend fun byIds(ids: Collection<String>): List<Resource> = coroutineScope {
        adapters.map { async { runCatching { it.byIds(ids) }.getOrElse { emptyList() } } }.awaitAll().flatten()
    }

    override fun observeBookmarkIds(): Flow<Set<String>> = dao.observeFlags().map { f -> f.filter { it.bookmarked }.map { it.resourceId }.toSet() }

    override suspend fun toggleBookmark(resourceId: String) {
        val cur = dao.flag(resourceId)
        dao.upsertFlag(ResourceFlagEntity(resourceId, !(cur?.bookmarked ?: false), cur?.offline ?: false, cur?.cachedJson))
    }

    /** Offline: cache the metadata now; file download is handled by the sync worker once real URLs exist. */
    override suspend fun setOffline(resourceId: String, offline: Boolean) {
        val cur = dao.flag(resourceId)
        val cached = if (offline) byIds(listOf(resourceId)).firstOrNull()?.let { json.encodeToString(Resource.serializer(), it) } else null
        dao.upsertFlag(ResourceFlagEntity(resourceId, cur?.bookmarked ?: false, offline, cached))
    }
}
