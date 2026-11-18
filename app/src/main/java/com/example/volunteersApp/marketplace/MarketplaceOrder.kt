package com.example.volunteersApp.marketplace

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Locale

/** `marketplace_orders/{orderId}` status values; documents are written only by the server. */
object MarketplaceOrderStatus {
    const val PAID = "PAID"
    const val DELIVERED = "DELIVERED"
    const val HELP_REQUESTED = "HELP_REQUESTED"
    const val HELP_DECLINED = "HELP_DECLINED"
    const val REFUNDED = "REFUNDED"
    const val CLOSED = "CLOSED"
}

/** Buyer help reasons accepted by `requestMarketplaceOrderHelp`. */
enum class MarketplaceOrderHelpReason(val value: String, val label: String) {
    NOT_RECEIVED("NOT_RECEIVED", "I didn't receive it"),
    CANCEL("CANCEL", "I want to cancel"),
    OTHER("OTHER", "Something else"),
}

const val MARKETPLACE_ORDER_HELP_MESSAGE_MAX_LENGTH = 1_000

/** Minimal private order projection shown in My purchases / My sales. */
data class MarketplaceOrder(
    val id: String,
    val itemId: String,
    val buyerId: String,
    val sellerId: String,
    val title: String,
    val amountUsd: Double,
    val sellerNetUsd: Double,
    val imageUrl: String,
    val status: String,
    val helpReason: String,
    val helpMessage: String,
    val createdAtMillis: Long?,
) {
    val canMarkDelivered: Boolean get() = status == MarketplaceOrderStatus.PAID
    val canRequestHelp: Boolean
        get() = status == MarketplaceOrderStatus.PAID || status == MarketplaceOrderStatus.DELIVERED
    val canRespondToHelp: Boolean get() = status == MarketplaceOrderStatus.HELP_REQUESTED
    val isOpenForAdmin: Boolean
        get() = status == MarketplaceOrderStatus.HELP_REQUESTED || status == MarketplaceOrderStatus.HELP_DECLINED

    val statusLabel: String
        get() = when (status) {
            MarketplaceOrderStatus.PAID -> "Paid"
            MarketplaceOrderStatus.DELIVERED -> "Delivered"
            MarketplaceOrderStatus.HELP_REQUESTED -> "Help requested"
            MarketplaceOrderStatus.HELP_DECLINED -> "Under platform review"
            MarketplaceOrderStatus.REFUNDED -> "Refunded"
            MarketplaceOrderStatus.CLOSED -> "Closed"
            else -> status.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
        }
}

fun parseMarketplaceOrder(doc: DocumentSnapshot): MarketplaceOrder? {
    val data = doc.data ?: return null
    fun str(key: String) = (data[key] as? String)?.trim().orEmpty()
    fun num(key: String) = (data[key] as? Number)?.toDouble() ?: 0.0
    return MarketplaceOrder(
        id = doc.id,
        itemId = str("itemId"),
        buyerId = str("buyerId"),
        sellerId = str("sellerId"),
        title = str("title"),
        amountUsd = num("amountUsd"),
        sellerNetUsd = num("sellerNetUsd"),
        imageUrl = str("imageUrl"),
        status = str("status").uppercase(Locale.US),
        helpReason = str("helpReason"),
        helpMessage = str("helpMessage"),
        createdAtMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time
            ?: (data["soldAt"] as? Timestamp)?.toDate()?.time,
    )
}
