package com.anisync.android.data.diagnostics

object DiagnosticCategorySanitizer {
    const val REDACTED = "<redacted>"
    const val UNKNOWN = "unknown"

    private val forbiddenMarkers = listOf(
        "access_token",
        "refresh_token",
        "authorization_code",
        "authorization: bearer",
        "bearer ",
        "pkce",
        "verifier",
        "challenge",
        "client_id",
        "account_id",
        "mal_user_id",
        "userid",
        "user_id",
        "username",
        "display_name",
        "oauth_state",
        "callback_url",
        "redirect_uri",
        "personal_list",
        "list_content",
        "raw_response",
        "response_body",
        "state=",
        "code=",
    )

    fun sanitize(value: String?): String {
        val normalized = value
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(80)
            .orEmpty()
        if (normalized.isBlank()) return UNKNOWN
        val lower = normalized.lowercase()
        if ("://" in lower || forbiddenMarkers.any { marker -> marker in lower }) return REDACTED
        if (normalized.length > 48 && normalized.none { character -> character.isWhitespace() }) {
            return REDACTED
        }
        return normalized.replace(Regex("[^A-Za-z0-9 _.-]"), "_")
    }
}
