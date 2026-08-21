package ch.jorisda.nestling.agent.pair

import android.net.Uri

data class EnrollPayload(val baseUrl: String, val code: String)

object EnrollPayloadParser {
    /**
     * Strict on purpose: the URL in this payload decides where a child's usage
     * data goes. https only, except localhost for development. Manual entry is
     * routed through here too, so a typed http:// URL is refused exactly like a
     * scanned one.
     */
    fun parse(raw: String): EnrollPayload? {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "nestling" || uri.host != "enroll") return null

        val url = uri.getQueryParameter("url")?.trimEnd('/') ?: return null
        val code = uri.getQueryParameter("code")?.uppercase() ?: return null
        if (code.isEmpty()) return null

        val isLocal = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
        if (!url.startsWith("https://") && !isLocal) return null

        return EnrollPayload(url, code)
    }
}
