package com.alexchurkin.truckremote.data.controller

import com.alexchurkin.truckremote.util.logD
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

private const val BROADCAST_ADDRESS = "255.255.255.255"

/**
 * Where the search for the server is sent: 255.255.255.255 goes only through the default interface, which may be
 * wrong (e.g. the phone shares a hotspot or has mobile data), so the broadcasts of every subnet are added.
 */
fun broadcastAddresses(): List<InetAddress> {
    val subnetBroadcasts = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .mapNotNull { it.broadcast }
    } catch (e: IOException) {
        logD("Can't get network interfaces: $e")
        emptyList()
    }
    return (listOf(InetAddress.getByName(BROADCAST_ADDRESS)) + subnetBroadcasts).distinct()
}
