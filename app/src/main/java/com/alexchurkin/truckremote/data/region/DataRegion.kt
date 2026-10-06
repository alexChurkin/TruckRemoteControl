package com.alexchurkin.truckremote.data.region

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * What may be sent to the internet from the country the device is in ([country]: ISO 3166 two-letter code,
 * null or empty if it isn't known).
 *
 * Yandex ads are shown only in the countries where Yandex sells them and no consent is asked for an ad that isn't
 * personalised: Russia, Belarus and the countries of Central Asia and the Caucasus listed below.
 * Statistics and crash reports (AppMetrica, servers in Russia) aren't sent from the countries whose laws ask for
 * a consent to such identifiers or restrict sending personal data to Russia, nor from Ukraine, where Yandex services
 * are blocked. An unknown country gets nothing of that: the app works the same without it.
 */
class DataRegion(country: String?) {

    private val code = country.orEmpty().uppercase(Locale.ROOT)

    val adsAllowed: Boolean = code in ADS_COUNTRIES

    val analyticsAllowed: Boolean = code.length == COUNTRY_CODE_LENGTH && code !in NO_ANALYTICS_COUNTRIES

    companion object {
        private const val COUNTRY_CODE_LENGTH = 2

        // Russia, Belarus, Kazakhstan, Kyrgyzstan, Uzbekistan, Tajikistan, Azerbaijan
        private val ADS_COUNTRIES = setOf("RU", "BY", "KZ", "KG", "UZ", "TJ", "AZ")

        // The European Union
        private val EU = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU",
            "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        )

        // The rest of the European Economic Area, the United Kingdom and Switzerland: the same rules as in the EU
        private val EUROPE_OTHER = setOf("IS", "LI", "NO", "GB", "CH")

        // Ukraine (Yandex is blocked), Turkey (KVKK) and Brazil (LGPD)
        private val OTHER = setOf("UA", "TR", "BR")

        // Other countries with strict personal data laws: Japan, South Korea, India, Serbia, Georgia, Armenia,
        // Moldova, Canada
        private val STRICT = setOf("JP", "KR", "IN", "RS", "GE", "AM", "MD", "CA")

        private val NO_ANALYTICS_COUNTRIES = EU + EUROPE_OTHER + OTHER + STRICT

        /**
         * The country of the mobile network the device is in, then of its SIM card, then of the system language
         * (a tablet without a SIM card). No permission and no location are needed for any of them.
         */
        fun detect(context: Context): DataRegion {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val country = listOf(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
                .firstOrNull { !it.isNullOrBlank() }
            return DataRegion(country)
        }
    }
}
