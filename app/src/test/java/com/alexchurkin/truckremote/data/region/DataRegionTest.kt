package com.alexchurkin.truckremote.data.region

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRegionTest {

    @Test
    fun `ads are shown only where Yandex sells them without a consent`() {
        listOf("RU", "by", "KZ", "kg", "UZ", "TJ", "AZ").forEach { assertTrue(it, DataRegion(it).adsAllowed) }
        listOf("DE", "PL", "GB", "US", "UA", "TR", "BR", "AM", "", null).forEach {
            assertFalse(it.toString(), DataRegion(it).adsAllowed)
        }
    }

    @Test
    fun `statistics aren't sent from the countries that restrict them, nor from an unknown one`() {
        listOf("RU", "BY", "KZ", "US", "au").forEach { assertTrue(it, DataRegion(it).analyticsAllowed) }
        listOf("DE", "pl", "FR", "ES", "IT", "CZ", "RO", "HU", "NL", "NO", "IS", "LI", "GB", "CH", "UA", "TR", "BR")
            .plus(listOf("JP", "KR", "IN", "RS", "GE", "AM", "MD", "ca"))
            .forEach { assertFalse(it, DataRegion(it).analyticsAllowed) }
        listOf("", null, "RUS").forEach { assertFalse(it.toString(), DataRegion(it).analyticsAllowed) }
    }
}
