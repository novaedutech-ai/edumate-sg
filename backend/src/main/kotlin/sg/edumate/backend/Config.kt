package sg.edumate.backend

import java.io.File
import java.util.Properties

/**
 * Settings come from environment variables first, then backend/secrets.properties.
 * The API key is never logged or returned by any endpoint.
 */
data class Config(
    val apiKey: String,
    val model: String,
    val host: String,
    val port: Int,
    /** If set, requests must send `Authorization: Bearer <devToken>`. */
    val devToken: String?,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        fun load(secretsFile: File = File("secrets.properties")): Config {
            val file = Properties().apply { if (secretsFile.exists()) secretsFile.inputStream().use { load(it) } }
            fun setting(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: file.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }

            val key = setting("ANTHROPIC_API_KEY")
            require(key != null && !key.contains("REPLACE_ME")) {
                "No Claude API key found. Create ${secretsFile.absolutePath} (copy secrets.properties.example) " +
                    "and set ANTHROPIC_API_KEY, or set the ANTHROPIC_API_KEY environment variable."
            }
            return Config(
                apiKey = key,
                model = setting("EDUMATE_MODEL") ?: DEFAULT_MODEL,
                // Loopback only: the Android emulator reaches it via 10.0.2.2; nothing else on the network can.
                host = setting("EDUMATE_HOST") ?: "127.0.0.1",
                port = setting("EDUMATE_PORT")?.toIntOrNull() ?: 8080,
                devToken = setting("EDUMATE_DEV_TOKEN"),
            )
        }
    }
}
