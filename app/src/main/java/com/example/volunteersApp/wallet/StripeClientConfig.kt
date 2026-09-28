package com.example.volunteersApp.wallet

import android.content.Context
import com.example.volunteersApp.BuildConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe

private const val STRIPE_KEY_MISSING_MESSAGE =
    "Stripe publishable key is missing. Add STRIPE_PUBLISHABLE_KEY to local.properties and rebuild the app."

fun resolveStripePublishableKey(): String = BuildConfig.STRIPE_PUBLISHABLE_KEY.trim()

fun ensureStripePaymentConfiguration(context: Context): String {
    val publishableKey = resolveStripePublishableKey()
    require(publishableKey.isNotBlank()) { STRIPE_KEY_MISSING_MESSAGE }
    PaymentConfiguration.init(context.applicationContext, publishableKey)
    return publishableKey
}

fun createStripeClient(context: Context): Stripe {
    val publishableKey = ensureStripePaymentConfiguration(context)
    return Stripe(context.applicationContext, publishableKey)
}
