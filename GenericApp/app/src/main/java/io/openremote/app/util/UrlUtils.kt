package io.openremote.app.util

object UrlUtils {
    fun isIpV6NoScheme(url: String): Boolean {
        val ipv6Pattern = Regex(
            "^(?:([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6}))$"
        )
        return ipv6Pattern.matches(url)
    }

    fun startsWithScheme(url: String): Boolean {
        val schemePattern = Regex("^[a-zA-Z]+://.*$")
        return schemePattern.matches(url)
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