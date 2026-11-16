package sg.edumate.app.data.resource

import kotlinx.serialization.json.Json
import sg.edumate.app.data.local.ResourceDao
import sg.edumate.app.data.local.UserResourceEntity
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.ResourceAdapter
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in for the MOE resource library. Production integration must use an official API with
 * school SSO (see docs/05-api/openapi.yaml `x-moe-integration`). This adapter never scrapes any site;
 * it returns clearly-labelled sample records with no URLs.
 */
@Singleton
class MockMoeLibraryAdapter @Inject constructor() : ResourceAdapter {
    override val kind = ResourceSourceKind.MOE_LIBRARY
    private val licence = Licence("Sample data – not a real resource", allowsModification = false, requiresAttribution = true)
    private val source = "MOE library (sample – integration pending)"

    private val items = listOf(
        r("moe-1", "Fractions with bar models", "Slides introducing related fractions using bar models.", Level.P4, "math", "p4-ma-fr", ResourceType.SLIDES),
        r("moe-2", "Algebra tiles interactive", "Manipulative-style activity for simplifying expressions.", Level.SEC1, "math", "s1-ma-ae", ResourceType.INTERACTIVE, listOf(Stream.G1, Stream.G2)),
        r("moe-3", "Cell structure reading", "Short reading on organelles with comprehension questions.", Level.SEC1, "science", "s1-sc-ce", ResourceType.READING),
        r("moe-4", "Water cycle diagram pack", "Labelled diagrams and a sequencing task.", Level.P5, "science", "p5-sc-wc", ResourceType.WORKSHEET),
        r("moe-5", "Kinematics graph matching", "Match motion descriptions to speed–time graphs.", Level.SEC3, "physics", "s3-ph-ki", ResourceType.ASSESSMENT, listOf(Stream.G2, Stream.G3)),
        r("moe-6", "Market equilibrium case study", "Case on hawker food prices and supply shocks.", Level.JC1, "economics", "j1-ec-ds", ResourceType.READING),
    )

    override suspend fun search(query: ResourceQuery) = items.filter(query::matches)
    override suspend fun byIds(ids: Collection<String>) = items.filter { it.id in ids }

    private fun r(id: String, title: String, summary: String, level: Level, subject: String, topic: String, type: ResourceType, streams: List<Stream> = emptyList()) =
        Resource(id, title, summary, kind, source, licence, "Sample record for development", level, subject, listOf(topic), type, streams, url = null, isMock = true)
}

/** Openly-licensed content. Sample records only; real adapter will query a curated OER index. */
@Singleton
class OpenLicensedContentAdapter @Inject constructor() : ResourceAdapter {
    override val kind = ResourceSourceKind.OPEN_LICENSED
    private val ccBy = Licence("CC BY 4.0 (sample)", "https://creativecommons.org/licenses/by/4.0/", allowsModification = true, requiresAttribution = true)

    private val items = listOf(
        Resource("oer-1", "Ratio problem set", "Twelve graded ratio problems with answers.", kind, "Open content (sample)", ccBy,
            "Sample attribution – replace with author/source", Level.SEC1, "math", listOf("s1-ma-rp"), ResourceType.WORKSHEET, emptyList(), isMock = true),
        Resource("oer-2", "Food web card sort", "Printable organism cards for building food webs.", kind, "Open content (sample)", ccBy,
            "Sample attribution – replace with author/source", Level.P6, "science", listOf("p6-sc-fw"), ResourceType.INTERACTIVE, emptyList(), isMock = true),
        Resource("oer-3", "Argument structure organiser", "Claim–evidence–reasoning graphic organiser.", kind, "Open content (sample)", ccBy,
            "Sample attribution – replace with author/source", Level.SEC1, "english", listOf("s1-el-aw"), ResourceType.WORKSHEET, listOf(Stream.G1, Stream.G2), isMock = true),
    )

    override suspend fun search(query: ResourceQuery) = items.filter(query::matches)
    override suspend fun byIds(ids: Collection<String>) = items.filter { it.id in ids }
}

/** Teacher-added resources (links to their own Drive files etc.). Stored in the encrypted DB. */
@Singleton
class UserUploadAdapter @Inject constructor(
    private val dao: ResourceDao, private val json: Json, private val ids: IdGenerator, private val clock: Clock,
) : ResourceAdapter {
    override val kind = ResourceSourceKind.USER_UPLOAD

    private suspend fun all() = dao.userResources().map { json.decodeFromString(Resource.serializer(), it.json) }
    override suspend fun search(query: ResourceQuery) = all().filter(query::matches)
    override suspend fun byIds(ids: Collection<String>) = all().filter { it.id in ids }

    suspend fun add(title: String, url: String?, type: ResourceType, level: Level, subjectId: String, topicId: String?): Resource {
        val r = Resource(
            id = "usr-" + ids.newId(), title = title.trim(), summary = "Added by you", sourceKind = kind, sourceName = "My resources",
            licence = Licence("Teacher-owned (school use)", allowsModification = true, requiresAttribution = false), attribution = "You",
            level = level, subjectId = subjectId, topicIds = listOfNotNull(topicId), type = type, streams = emptyList(),
            url = url?.takeIf { it.startsWith("https://") },
        )
        dao.upsertUserResource(UserResourceEntity(r.id, json.encodeToString(Resource.serializer(), r), clock.nowMs()))
        return r
    }
}
