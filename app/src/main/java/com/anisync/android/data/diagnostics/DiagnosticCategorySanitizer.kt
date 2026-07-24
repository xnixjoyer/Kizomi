package com.anisync.android.data.diagnostics

object DiagnosticCategorySanitizer {
    const val REDACTED = "<redacted>"
    const val UNKNOWN = "unknown"

    private val compoundCategoryPattern = Regex("[a-z0-9]+(?:_[a-z0-9]+)+")
    private val httpClassPattern = Regex("[1-5]xx")
    private val metadataPattern = Regex("[A-Za-z0-9./_+:-]{1,48}")
    private val safeAtomicCategories = setOf(
        "success",
        "failure",
        "cancelled",
        "pending",
        "completed",
        "available",
        "unavailable",
        "absent",
        "unknown",
        "none",
        "idle",
    )

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

    /**
     * Accepts only low-cardinality compound operation names such as `library_read`, a small set of
     * explicit atomic result categories, or HTTP classes such as `4xx`. Opaque single-token values
     * are rejected even when they contain no marker text.
     */
    fun sanitize(value: String?): String = sanitizeCategory(value)

    fun sanitizeCategory(value: String?): String {
        val normalized = normalize(value) ?: return UNKNOWN
        if (containsSensitiveShape(normalized)) return REDACTED
        return if (
            compoundCategoryPattern.matches(normalized) ||
            httpClassPattern.matches(normalized) ||
            normalized in safeAtomicCategories
        ) {
            normalized
        } else {
            REDACTED
        }
    }

    /**
     * Accepts short structural metadata only: build types, versions, enum-like values and redirect
     * components without query/fragment data. Long opaque base64/base64url/token/identifier shapes,
     * URLs, usernames, payloads and human-readable titles are rejected.
     */
    fun sanitizeMetadata(value: String?): String {
        val normalized = normalize(value) ?: return UNKNOWN
        if (containsSensitiveShape(normalized)) return REDACTED
        if (normalized.any(Char::isWhitespace)) return REDACTED
        if (normalized.any { it in charArrayOf('?', '#', '&', '=', '@', '{', '}', '[', ']', '"', '\'') }) {
            return REDACTED
        }
        if (normalized.length >= 20 && '/' !in normalized && ':' !in normalized) return REDACTED
        return if (metadataPattern.matches(normalized)) normalized else REDACTED
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun containsSensitiveShape(value: String): Boolean {
        val lower = value.lowercase()
        return "://" in lower || forbiddenMarkers.any { marker -> marker in lower }
    }
}
