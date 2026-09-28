package com.example.volunteersApp.streams

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

object LiveBroadcastPrecheck {

    data class Result(
        val cameraGranted: Boolean,
        val microphoneGranted: Boolean,
        val networkAvailable: Boolean,
    ) {
        val isReady: Boolean
            get() = cameraGranted && microphoneGranted && networkAvailable

        val issues: List<String>
            get() = buildList {
                if (!cameraGranted) add("Camera permission is required to go live.")
                if (!microphoneGranted) add("Microphone permission is required to go live.")
                if (!networkAvailable) add("An internet connection is required to go live.")
            }
    }

    fun evaluate(context: Context): Result {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val networkAvailable = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
        return Result(
            cameraGranted = cameraGranted,
            microphoneGranted = microphoneGranted,
            networkAvailable = networkAvailable,
        )
    }
}
