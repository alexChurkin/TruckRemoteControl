package com.alexchurkin.truckremote.data.controller

import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.util.isValidIpv4

/** Address of the server from the QR code shown in the server window: "truckremote://192.168.1.10:18250". */
data class ServerLink(val ip: String, val port: Int) {
    companion object {
        private const val SCHEME = "truckremote://"

        // Returns null if the text isn't a link of Truck Remote Server
        fun parse(text: String?): ServerLink? {
            val address = text?.trim()?.takeIf { it.startsWith(SCHEME, ignoreCase = true) }
                ?.substring(SCHEME.length)?.trimEnd('/') ?: return null
            val ip = address.substringBefore(':')
            val port = address.substringAfter(':', "").toIntOrNull()
            return if (isValidIpv4(ip) && port != null && port in AppSettings.PORT_RANGE) ServerLink(ip, port) else null
        }
    }
}
