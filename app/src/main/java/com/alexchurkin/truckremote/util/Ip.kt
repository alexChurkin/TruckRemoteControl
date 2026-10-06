package com.alexchurkin.truckremote.util

// Dotted IPv4 address like 192.168.1.10 (the server listens only on IPv4)
fun isValidIpv4(text: String): Boolean {
    val parts = text.split('.')
    return parts.size == IPV4_PARTS && parts.all { part ->
        part.length in 1..MAX_PART_DIGITS && part.all(Char::isDigit) && part.toInt() <= MAX_PART_VALUE
    }
}

private const val IPV4_PARTS = 4
private const val MAX_PART_DIGITS = 3
private const val MAX_PART_VALUE = 255
