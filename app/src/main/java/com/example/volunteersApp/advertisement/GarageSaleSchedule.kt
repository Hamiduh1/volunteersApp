package com.example.volunteersApp.advertisement

import android.content.Context
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val GARAGE_SALE_TITLE_MAX = 120
internal const val GARAGE_SALE_DESCRIPTION_MAX = 2_000
internal const val GARAGE_SALE_NOTES_MAX = 300
internal const val GARAGE_SALE_MAX_CATEGORIES = 8
internal const val GARAGE_SALE_MAX_MEDIA = 8
internal const val GARAGE_SALE_FIRST_DAY_MAX_AHEAD_DAYS = 60
internal const val GARAGE_SALE_MAX_EXTRA_DAYS = 7
internal const val GARAGE_SALE_PHONE_MIN_DIGITS = 7
internal const val GARAGE_SALE_PHONE_MAX_DIGITS = 15
internal const val GARAGE_SALE_SAFETY_NOTE =
    "Shop in daylight, check items before paying; for mobile money/transfers the seller confirms money arrived before handing over."
internal const val GARAGE_SALE_CHECKOUT_RETURN_NOTE =
    "If you finished paying, your receipt appears in Wallet once Stripe confirms. If you cancelled, you weren't charged."

enum class GarageSaleType(val wire: String, val label: String) {
    GARAGE("GARAGE", "Garage sale"),
    YARD("YARD", "Yard sale"),
    MOVING("MOVING", "Moving sale"),
    ESTATE("ESTATE", "Estate sale"),
    MULTI_FAMILY("MULTI_FAMILY", "Multi-family sale"),
    COMMUNITY("COMMUNITY", "Community sale");

    companion object {
        fun fromWire(raw: String?): GarageSaleType {
            val clean = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return entries.firstOrNull { it.wire == clean } ?: GARAGE
        }
    }
}

enum class GarageSaleCategory(val wire: String, val label: String) {
    FURNITURE("FURNITURE", "Furniture"),
    CLOTHING("CLOTHING", "Clothing"),
    KIDS("KIDS", "Kids & baby"),
    ELECTRONICS("ELECTRONICS", "Electronics"),
    APPLIANCES("APPLIANCES", "Appliances"),
    KITCHEN("KITCHEN", "Kitchen"),
    TOOLS("TOOLS", "Tools"),
    BOOKS("BOOKS", "Books & media"),
    SPORTS("SPORTS", "Sports & outdoors"),
    DECOR("DECOR", "Home decor"),
    ANTIQUES("ANTIQUES", "Antiques"),
    FREE("FREE", "Free stuff");

    companion object {
        fun fromWire(raw: String?): GarageSaleCategory? {
            val clean = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return entries.firstOrNull { it.wire == clean }
        }
    }
}

enum class GaragePaymentOption(val wire: String, val label: String) {
    CASH("CASH", "Cash"),
    IN_APP_CARD("IN_APP_CARD", "Card in app"),
    MOBILE_MONEY("MOBILE_MONEY", "Mobile money"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank transfer");

    companion object {
        fun fromWire(raw: String?): GaragePaymentOption? {
            val clean = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return entries.firstOrNull { it.wire == clean }
        }
    }
}

/** Schedule and listing extras sent with a create or edit; `startsAtMs`/`endsAtMs` always travel together. */
data class GarageSaleScheduleInput(
    val saleType: GarageSaleType,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val categories: List<GarageSaleCategory>,
    val paymentOptions: List<GaragePaymentOption>,
    val notes: String,
)

val GarageSale.saleTypeValue: GarageSaleType get() = GarageSaleType.fromWire(saleType)

val GarageSale.categoryValues: List<GarageSaleCategory>
    get() = categories.mapNotNull { GarageSaleCategory.fromWire(it) }.distinct()

/** Missing or empty means in-app card, as older listings behaved. */
val GarageSale.paymentOptionValues: List<GaragePaymentOption>
    get() = paymentOptions.mapNotNull { GaragePaymentOption.fromWire(it) }.distinct()
        .ifEmpty { listOf(GaragePaymentOption.IN_APP_CARD) }

val GarageSale.acceptsInAppCard: Boolean
    get() = GaragePaymentOption.IN_APP_CARD in paymentOptionValues

val GarageSale.isDated: Boolean get() = startsAt != null && endsAt != null

fun GarageSale.isEnded(nowMs: Long = System.currentTimeMillis()): Boolean {
    if (status.trim().uppercase(Locale.US) == "ENDED") return true
    val end = endsAt?.toDate()?.time ?: return false
    return nowMs >= end
}

internal fun isGarageSaleDocEnded(data: Map<String, Any?>, nowMs: Long = System.currentTimeMillis()): Boolean {
    if ((data["status"] as? String)?.trim()?.uppercase(Locale.US) == "ENDED") return true
    val end = (data["endsAt"] as? Timestamp)?.toDate()?.time ?: return false
    return nowMs >= end
}

sealed class GarageSaleTiming {
    data object Ended : GarageSaleTiming()
    data object Undated : GarageSaleTiming()
    data class OpenNow(val untilMs: Long) : GarageSaleTiming()
    data class Upcoming(val opensAtMs: Long) : GarageSaleTiming()
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun addDays(dayStartMs: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dayStartMs
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

private fun minuteOfDay(ms: Long): Int = Calendar.getInstance().run {
    timeInMillis = ms
    get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
}

/** Local time on [dayStartMs] at [minutes] past midnight. */
internal fun garageDayAt(dayStartMs: Long, minutes: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dayStartMs
    set(Calendar.HOUR_OF_DAY, minutes / 60)
    set(Calendar.MINUTE, minutes % 60)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun garageStartOfDay(ms: Long): Long = startOfDay(ms)

internal fun garageAddDays(dayStartMs: Long, days: Int): Long = addDays(dayStartMs, days)

internal fun garageMinuteOfDay(ms: Long): Int = minuteOfDay(ms)

private fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

/** Daily hours repeat each sale day; when closing isn't after opening the sale is one continuous window. */
fun GarageSale.timing(nowMs: Long = System.currentTimeMillis()): GarageSaleTiming {
    if (isEnded(nowMs)) return GarageSaleTiming.Ended
    val start = startsAt?.toDate()?.time
    val end = endsAt?.toDate()?.time
    if (start == null || end == null) return GarageSaleTiming.Undated
    val opens = minuteOfDay(start)
    val closes = minuteOfDay(end)
    if (closes <= opens) {
        return if (nowMs < start) GarageSaleTiming.Upcoming(start) else GarageSaleTiming.OpenNow(end)
    }
    val lastDay = startOfDay(end)
    var day = maxOf(startOfDay(nowMs), startOfDay(start))
    var guard = 0
    while (day <= lastDay && guard < 400) {
        val openAt = garageDayAt(day, opens)
        val closeAt = garageDayAt(day, closes)
        if (nowMs < openAt) return GarageSaleTiming.Upcoming(openAt)
        if (nowMs < closeAt) return GarageSaleTiming.OpenNow(closeAt)
        day = addDays(day, 1)
        guard++
    }
    return GarageSaleTiming.Ended
}

internal fun garageTimeLabel(ms: Long): String {
    val minutes = minuteOfDay(ms) % 60
    val pattern = if (minutes == 0) "h a" else "h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}

private fun garageDayName(ms: Long, nowMs: Long): String {
    val days = ((startOfDay(ms) - startOfDay(nowMs)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Tomorrow"
        days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(ms))
    }
}

fun GarageSaleTiming.label(nowMs: Long = System.currentTimeMillis()): String = when (this) {
    GarageSaleTiming.Ended -> "Ended"
    GarageSaleTiming.Undated -> "Dates not set"
    is GarageSaleTiming.OpenNow -> "Open now · until ${garageTimeLabel(untilMs)}"
    is GarageSaleTiming.Upcoming -> if (isSameDay(opensAtMs, nowMs)) {
        "Today · opens ${garageTimeLabel(opensAtMs)}"
    } else {
        "${garageDayName(opensAtMs, nowMs)} · ${garageTimeLabel(opensAtMs)}"
    }
}

/** Short badge for tiles: "Open now" or the day. */
fun GarageSaleTiming.shortLabel(nowMs: Long = System.currentTimeMillis()): String = when (this) {
    GarageSaleTiming.Ended -> "Ended"
    GarageSaleTiming.Undated -> "Anytime"
    is GarageSaleTiming.OpenNow -> "Open now"
    is GarageSaleTiming.Upcoming -> garageDayName(opensAtMs, nowMs)
}

/** Day shown in the calendar block: today while open, the next opening, or the last day once ended. */
fun GarageSale.calendarDayMs(nowMs: Long = System.currentTimeMillis()): Long? = when (val t = timing(nowMs)) {
    is GarageSaleTiming.OpenNow -> nowMs
    is GarageSaleTiming.Upcoming -> t.opensAtMs
    GarageSaleTiming.Ended -> endsAt?.toDate()?.time ?: startsAt?.toDate()?.time
    GarageSaleTiming.Undated -> null
}

fun GarageSale.saleDayCount(): Int {
    val start = startsAt?.toDate()?.time ?: return 0
    val end = endsAt?.toDate()?.time ?: return 0
    return (((startOfDay(end) - startOfDay(start)) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
}

fun garageScheduleSummary(startMs: Long, endMs: Long): String {
    val day = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val opens = minuteOfDay(startMs)
    val closes = minuteOfDay(endMs)
    if (closes <= opens) {
        return "${day.format(Date(startMs))}, ${garageTimeLabel(startMs)} – ${day.format(Date(endMs))}, ${garageTimeLabel(endMs)}"
    }
    val hours = "${garageTimeLabel(startMs)} – ${garageTimeLabel(endMs)}"
    return if (isSameDay(startMs, endMs)) {
        "${day.format(Date(startMs))} · $hours"
    } else {
        "${day.format(Date(startMs))} – ${day.format(Date(endMs))} · $hours"
    }
}

fun GarageSale.scheduleSummary(): String? {
    val start = startsAt?.toDate()?.time ?: return null
    val end = endsAt?.toDate()?.time ?: return null
    return garageScheduleSummary(start, end)
}

enum class GarageDayFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    WEEKEND("This weekend"),
    UPCOMING("Upcoming"),
    SAVED("Saved"),
}

/** This Saturday 00:00 to Monday 00:00; on weekdays, the coming weekend. */
private fun weekendWindow(nowMs: Long): Pair<Long, Long> {
    val today = startOfDay(nowMs)
    val dow = Calendar.getInstance().run { timeInMillis = today; get(Calendar.DAY_OF_WEEK) }
    val saturday = when (dow) {
        Calendar.SATURDAY -> today
        Calendar.SUNDAY -> addDays(today, -1)
        else -> addDays(today, Calendar.SATURDAY - dow)
    }
    return saturday to addDays(saturday, 2)
}

fun GarageSale.matchesDayFilter(
    filter: GarageDayFilter,
    savedIds: Set<String>,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    val t = timing(nowMs)
    return when (filter) {
        GarageDayFilter.ALL -> true
        GarageDayFilter.SAVED -> id in savedIds
        GarageDayFilter.TODAY -> t is GarageSaleTiming.OpenNow ||
            (t is GarageSaleTiming.Upcoming && isSameDay(t.opensAtMs, nowMs))
        GarageDayFilter.UPCOMING -> t is GarageSaleTiming.Upcoming && !isSameDay(t.opensAtMs, nowMs)
        GarageDayFilter.WEEKEND -> {
            if (t == GarageSaleTiming.Ended || t == GarageSaleTiming.Undated) return false
            val start = startsAt?.toDate()?.time ?: return false
            val end = endsAt?.toDate()?.time ?: return false
            val (weekendStart, weekendEnd) = weekendWindow(nowMs)
            maxOf(start, weekendStart, nowMs) < minOf(end, weekendEnd)
        }
    }
}

/** Open now, then later today, then upcoming by opening time, then undated newest first; ended last. */
fun sortGarageSales(sales: List<GarageSale>, nowMs: Long = System.currentTimeMillis()): List<GarageSale> {
    val posted: (GarageSale) -> Long = { it.timestamp?.toDate()?.time ?: 0L }
    return sales.map { it to it.timing(nowMs) }
        .sortedWith(
            compareBy<Pair<GarageSale, GarageSaleTiming>> { (_, t) ->
                when (t) {
                    is GarageSaleTiming.OpenNow -> 0
                    is GarageSaleTiming.Upcoming -> if (isSameDay(t.opensAtMs, nowMs)) 1 else 2
                    GarageSaleTiming.Undated -> 3
                    GarageSaleTiming.Ended -> 4
                }
            }.thenBy { (sale, t) ->
                when (t) {
                    is GarageSaleTiming.OpenNow -> t.untilMs
                    is GarageSaleTiming.Upcoming -> t.opensAtMs
                    else -> -posted(sale)
                }
            }
        )
        .map { it.first }
}

fun GarageSale.matchesGarageSaleQuery(query: String): Boolean {
    val t = query.trim().lowercase(Locale.getDefault())
    if (t.isEmpty()) return true
    val fields = listOf(title, description, shopName, contactName, address, city, state, postalCode, notes, saleTypeValue.label) +
        categoryValues.map { it.label }
    return fields.any { it.lowercase(Locale.getDefault()).contains(t) }
}

fun GarageSale.areaLabel(): String =
    listOf(city, state).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        .ifBlank { address.trim() }

fun GarageSale.distanceKmFrom(location: Pair<Double, Double>?): Double? {
    val loc = location ?: return null
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat - loc.first)
    val dLon = Math.toRadians(lng - loc.second)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(loc.first)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2.0)
    return 2 * asin(sqrt(a)) * earthRadiusKm
}

fun garageDistanceLabel(km: Double?): String? = when {
    km == null -> null
    km < 1.0 -> "<1 km away"
    km < 10.0 -> "${"%.1f".format(Locale.US, km)} km away"
    else -> "${km.toInt()} km away"
}

fun garagePhoneDigits(raw: String): Int = raw.count { it.isDigit() }

/** Bookmarks live on the device only, like iOS. */
object GarageSavedSales {
    private const val PREFS = "garage_sale_saved"
    private const val KEY_IDS = "ids"
    private val state = MutableStateFlow<Set<String>?>(null)

    fun ids(context: Context): StateFlow<Set<String>?> {
        if (state.value == null) {
            state.value = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_IDS, emptySet())
                .orEmpty()
                .toSet()
        }
        return state.asStateFlow()
    }

    fun toggle(context: Context, saleId: String) {
        if (saleId.isBlank()) return
        val current = ids(context).value.orEmpty()
        val next = if (saleId in current) current - saleId else current + saleId
        state.value = next
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IDS, next)
            .apply()
    }
}
