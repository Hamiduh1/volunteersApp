package com.example.volunteersApp.wallet

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Parses user-typed money amounts. Many corridor keyboards (French, Portuguese) type a
 * comma as the decimal key, so "12,50" must mean 12.5 rather than 1250.
 * Mirrors iOS `WalletAmountParser`.
 */
object WalletAmountParser {
    private val ignoredCharacters = charArrayOf(' ', '\u00A0', '\u202F', '\'', '\u2019')

    /** Returns 0.0 for anything that is not a finite amount greater than zero. */
    fun parse(text: String?, locale: Locale = Locale.getDefault()): Double {
        val cleaned = text.orEmpty().trim().filterNot { it in ignoredCharacters }
        if (cleaned.isEmpty()) return 0.0

        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                // Both present: the right-most one is the decimal separator.
                if (lastComma > lastDot) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                val commaCount = cleaned.count { it == ',' }
                val fractionDigits = cleaned.length - lastComma - 1
                val localeUsesCommaDecimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
                if (commaCount == 1 && (localeUsesCommaDecimal || fractionDigits != 3)) {
                    cleaned.replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastDot >= 0 -> {
                if (cleaned.count { it == '.' } > 1) cleaned.replace(".", "") else cleaned
            }
            else -> cleaned
        }

        val value = normalized.toDoubleOrNull() ?: return 0.0
        return if (value.isFinite() && value > 0) value else 0.0
    }

    /** Null instead of 0.0 when the text is not a usable amount. */
    fun parseOrNull(text: String?, locale: Locale = Locale.getDefault()): Double? =
        parse(text, locale).takeIf { it > 0 }
}
