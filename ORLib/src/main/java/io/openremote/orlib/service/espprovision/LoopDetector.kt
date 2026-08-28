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
package io.openremote.orlib.service.espprovision

import java.util.Date
import java.util.concurrent.TimeUnit

class LoopDetector(
  private val timeout: Long = TimeUnit.MINUTES.toSeconds(2),
  private val maxIterations: Int = 25,
) {

  private var startTime: Date? = null
  private var iterationCount = 0

  fun reset() {
    startTime = Date()
    iterationCount = 0
  }

  fun detectLoop(): Boolean {
    iterationCount++
    if (iterationCount > maxIterations) {
      return true
    }
    val start = startTime ?: return true
    if ((Date().time - start.time) / 1000 > timeout) {
      return true
    }
    return false
  }
}
