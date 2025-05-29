package io.openremote.orlib.service.espprovision

import io.openremote.orlib.service.ESPProvisionProvider

class CallbackChannel(private val espProvisionCallback: ESPProvisionProvider.ESPProvisionCallback, private val provider: String) {

    fun sendMessage(action: String, data: Map<String, Any>? = null) {
        var payload: MutableMap<String, Any> = hashMapOf(
            "action" to action,
            "provider" to "espprovision")

        data?.let { payload.putAll(it) }

        espProvisionCallback.accept(payload)
    }
}