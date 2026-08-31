/*
 * Copyright 2026, OpenRemote Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.openremote.app.util

object UrlUtils {
  fun isIpV6NoScheme(url: String): Boolean {
    val ipv6Pattern =
      Regex(
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
