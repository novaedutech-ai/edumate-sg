package sg.edumate.backend

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import org.slf4j.LoggerFactory

enum class Effort { LOW, MEDIUM, HIGH }

/** One structured generation call. Returns JSON text that matches [schema]. Kept SDK-free for tests. */
fun interface StructuredLlm {
    fun generate(effort: Effort, system: String, user: String, schema: Map<String, Any?>): String
}

/**
 * Claude via the official Java SDK, using structured outputs so the response is guaranteed to match
 * the schema derived from the app's data classes.
 *
 * - Streaming, so long worksheets never hit HTTP timeouts.
 * - Adaptive thinking (the default on this model); depth is set with `effort`.
 * - The system prompt is marked for prompt caching (it is identical on every request).
 * - Server-side refusal fallbacks are on (`fallbacks: "default"`), so a safety classifier false positive
 *   on ordinary teaching content is retried on a suitable model instead of failing.
 */
class ClaudeStructuredLlm(private val client: AnthropicClient, private val model: String) : StructuredLlm {
    private val log = LoggerFactory.getLogger("Claude")

    override fun generate(effort: Effort, system: String, user: String, schema: Map<String, Any?>): String {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(64000L)
            .systemOfTextBlockParams(listOf(
                TextBlockParam.builder().text(system).cacheControl(CacheControlEphemeral.builder().build()).build(),
            ))
            .outputConfig(
                OutputConfig.builder()
                    .effort(when (effort) {
                        Effort.LOW -> OutputConfig.Effort.LOW
                        Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
                        Effort.HIGH -> OutputConfig.Effort.HIGH
                    })
                    .format(
                        JsonOutputFormat.builder()
                            .schema(JsonOutputFormat.Schema.builder()
                                .putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) })
                                .build())
                            .build(),
                    )
                    .build(),
            )
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(user)
            .build()

        val text = StringBuilder()
        var stopReason: StopReason? = null
        var refusalExplanation: String? = null
        val started = System.currentTimeMillis()

        client.messages().createStreaming(params).use { response ->
            response.stream().forEach { event ->
                event.contentBlockDelta().ifPresent { d -> d.delta().text().ifPresent { text.append(it.text()) } }
                event.messageDelta().ifPresent { m ->
                    m.delta().stopReason().ifPresent { stopReason = it }
                    m.delta().stopDetails().ifPresent { refusalExplanation = it.explanation().orElse(null) }
                }
            }
        }
        log.info("generated {} chars in {} ms (effort={}, stop={})", text.length, System.currentTimeMillis() - started, effort, stopReason)

        when (stopReason) {
            StopReason.REFUSAL -> throw ContentBlockedException(
                "The AI declined this request${refusalExplanation?.let { ": $it" } ?: ""}. Try rephrasing your notes.")
            StopReason.MAX_TOKENS -> throw ModelOutputException("The response was too long. Try fewer questions or streams.")
            else -> Unit
        }
        return text.toString()
    }
}
