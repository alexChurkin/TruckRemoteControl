package com.alexchurkin.truckremote.util

// Dotted IPv4 address like 192.168.1.10 (the server listens only on IPv4)
fun isValidIpv4(text: String): Boolean {
    val parts = text.split('.')
    return parts.size == 4 && parts.all { part ->
        part.length in 1..3 && part.all(Char::isDigit) && part.toInt() <= 255
    }
}
