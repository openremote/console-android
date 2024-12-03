package io.openremote.app.util

object UrlUtils {
    fun isIpAddress(url: String): Boolean {
        val ipPattern = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(:\\d{1,5})?$"
        )
        return ipPattern.matches(url)
    }
    fun startsWithHttp(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun endsWithTld(url: String): Boolean {
        val tldPattern = Regex(
            "(?:[a-zA-Z]*\\.)+([a-zA-Z]+)(?:\\/.*)?"
        )
        return tldPattern.matches(url)
    }
}