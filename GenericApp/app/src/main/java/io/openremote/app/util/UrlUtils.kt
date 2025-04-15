package io.openremote.app.util

import android.webkit.URLUtil

object UrlUtils {
    fun isIpAddress(url: String): Boolean {
        val ipPattern = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(:\\d{1,5})?$"
        )
        return ipPattern.matches(url)
    }

    fun isIpV6NoScheme(url: String): Boolean {
        val ipv6Pattern = Regex(
            "^(?:([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6}))$"
        )
        return ipv6Pattern.matches(url)
    }

    fun startsWithHttp(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun startsWithScheme(url: String): Boolean {
        val schemePattern = Regex("^[a-zA-Z]+://.*$")
        return schemePattern.matches(url)
    }

    fun endsWithTld(url: String): Boolean {
        val tldPattern = Regex(
            "(?:[a-zA-Z]*\\.)+([a-zA-Z]+)(?:\\/.*)?"
        )
        return tldPattern.matches(url)
    }

    fun hostToUrl(host: String): String {
        return when {
            isIpV6NoScheme(host) -> "https://[${host}]"
            startsWithScheme(host) ->
                if (host.contains(".") || host.contains("[")) host else "${host}.openremote.app"
            (host.contains(".") || host.contains("[")) -> "https://${host}"
            else -> "https://${host}.openremote.app"
        }
    }
}