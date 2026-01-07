package io.openremote.orlib.service.espprovision

import java.util.Date
import java.util.concurrent.TimeUnit

class LoopDetector(
    private val timeout: Long = TimeUnit.MINUTES.toSeconds(2),
    private val maxIterations: Int = 25) {

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
