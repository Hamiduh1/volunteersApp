package com.example.volunteersApp.wallet

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
// this file is not use, have correctly moved this logic to
// the server-side inside your getSecureExchangeRate Cloud Function.
/**
 * Modern Interface for External Exchange Rate API.
 * Used to calculate accurate cross-border fees for global volunteers.
 */
interface CurrencyService {
    /**
     * URL Structure: https://v6.exchangerate-api.com/v6/{apiKey}/pair/{base}/{target}
     * This endpoint is perfect for our "Hidden Profit" strategy because it provides
     * the raw market rate which we then adjust by 3%.
     */
    @GET("v6/{apiKey}/pair/{base}/{target}")
    suspend fun getExchangeRate(
        @Path("apiKey") apiKey: String,
        @Path("base") base: String,
        @Path("target") target: String
    ): ExchangeRateResponse
}

/**
 * Robust Data Model for currency responses.
 */
data class ExchangeRateResponse(
    @SerializedName("result")
    val result: String,

    @SerializedName("conversion_rate")
    val conversionRate: Double,

    // NEW: Useful if we decide to let the API handle the multiplication
    @SerializedName("conversion_result")
    val conversionResult: Double? = null,

    @SerializedName("base_code")
    val baseCode: String? = null,

    @SerializedName("target_code")
    val targetCode: String? = null,

    @SerializedName("time_last_update_utc")
    val lastUpdate: String? = null
)
