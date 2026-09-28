package com.example.volunteersApp.wallet

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

object WalletSecurityService {
    private const val TAG = "WalletSecurityService"
    private const val PREFS_FILE = "wallet_security_secure_v1"

    private const val KEY_PASSCODE_HASH = "passcode_hash"
    private const val KEY_PASSCODE_SALT = "passcode_salt"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKED_UNTIL_MS = "locked_until_ms"
    private const val KEY_SESSION_EXPIRES_AT_MS = "session_expires_at_ms"

    const val PASSCODE_LENGTH = 6
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS: Long = 5 * 60 * 1000
    const val SESSION_DURATION_MS: Long = 10 * 60 * 1000
    const val SECURE_STORAGE_UNAVAILABLE_MESSAGE =
        "Secure device storage is unavailable. Wallet access cannot be configured on this device."

    private val lifecycleObserverInstalled = AtomicBoolean(false)

    data class LockoutState(
        val isLocked: Boolean,
        val remainingMs: Long
    )

    sealed class PasscodeVerifyResult {
        data object Success : PasscodeVerifyResult()
        data class Failed(
            val attemptsRemaining: Int,
            val lockoutRemainingMs: Long
        ) : PasscodeVerifyResult()

        data class Locked(
            val remainingMs: Long
        ) : PasscodeVerifyResult()

        data object StorageUnavailable : PasscodeVerifyResult()
    }

    fun installProcessLifecycleObserver(application: Application) {
        if (!lifecycleObserverInstalled.compareAndSet(false, true)) return

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                expireWalletSession(application)
                Log.d(TAG, "Wallet session expired on app background/inactive")
            }
        })
    }

    fun normalizePasscode(raw: String?): String = raw.orEmpty().filter { it.isDigit() }.take(PASSCODE_LENGTH)

    fun hasWalletPasscode(context: Context): Boolean {
        val prefs = securePrefsOrNull(context) ?: return false
        return !prefs.getString(KEY_PASSCODE_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_PASSCODE_SALT, null).isNullOrBlank()
    }

    fun isWalletSessionValid(context: Context): Boolean {
        val prefs = securePrefsOrNull(context) ?: return false
        val expiresAt = prefs.getLong(KEY_SESSION_EXPIRES_AT_MS, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            expireWalletSession(context)
            return false
        }
        return true
    }

    fun markWalletSessionUnlocked(context: Context): Boolean {
        val expiresAt = System.currentTimeMillis() + SESSION_DURATION_MS
        val prefs = securePrefsOrNull(context) ?: return false
        prefs.edit()
            .putLong(KEY_SESSION_EXPIRES_AT_MS, expiresAt)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL_MS, 0L)
            .apply()
        return true
    }

    fun expireWalletSession(context: Context) {
        securePrefsOrNull(context)?.edit()?.remove(KEY_SESSION_EXPIRES_AT_MS)?.apply()
    }

    fun isBiometricUnlockEnabled(context: Context): Boolean =
        securePrefsOrNull(context)?.getBoolean(KEY_BIOMETRIC_ENABLED, false) ?: false

    fun setBiometricUnlockEnabled(context: Context, enabled: Boolean) {
        securePrefsOrNull(context)?.edit()?.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)?.apply()
    }

    fun currentLockoutState(context: Context): LockoutState {
        val now = System.currentTimeMillis()
        val lockedUntil = securePrefsOrNull(context)?.getLong(KEY_LOCKED_UNTIL_MS, 0L) ?: 0L
        if (lockedUntil <= now) {
            return LockoutState(isLocked = false, remainingMs = 0L)
        }
        return LockoutState(isLocked = true, remainingMs = lockedUntil - now)
    }

    fun setWalletPasscode(context: Context, passcodeRaw: String): Result<Unit> {
        val passcode = normalizePasscode(passcodeRaw)
        if (passcode.length != PASSCODE_LENGTH) {
            return Result.failure(IllegalArgumentException("Passcode must be exactly 6 digits."))
        }
        val prefs = securePrefsOrNull(context)
            ?: return Result.failure(IllegalStateException(SECURE_STORAGE_UNAVAILABLE_MESSAGE))

        return runCatching {
            val salt = randomSalt()
            val hash = hashPasscode(passcode, salt)

            prefs.edit()
                .putString(KEY_PASSCODE_SALT, salt)
                .putString(KEY_PASSCODE_HASH, hash)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL_MS, 0L)
                .apply()
        }
    }

    fun verifyWalletPasscode(context: Context, passcodeRaw: String): PasscodeVerifyResult {
        val lockout = currentLockoutState(context)
        if (lockout.isLocked) {
            return PasscodeVerifyResult.Locked(lockout.remainingMs)
        }
        val prefs = securePrefsOrNull(context) ?: return PasscodeVerifyResult.StorageUnavailable

        val passcode = normalizePasscode(passcodeRaw)
        if (passcode.length != PASSCODE_LENGTH) {
            val attemptsUsed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            return handleFailedAttempt(prefs, attemptsUsed)
        }

        val storedSalt = prefs.getString(KEY_PASSCODE_SALT, null)
        val storedHash = prefs.getString(KEY_PASSCODE_HASH, null)

        if (storedSalt.isNullOrBlank() || storedHash.isNullOrBlank()) {
            return PasscodeVerifyResult.Failed(
                attemptsRemaining = MAX_FAILED_ATTEMPTS,
                lockoutRemainingMs = 0L
            )
        }

        val candidateHash = hashPasscode(passcode, storedSalt)
        val matches = constantTimeEquals(storedHash, candidateHash)

        return if (matches) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL_MS, 0L)
                .apply()
            PasscodeVerifyResult.Success
        } else {
            val attemptsUsed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            handleFailedAttempt(prefs, attemptsUsed)
        }
    }

    fun canVerifyDeviceOwner(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Device-owner verification availability check failed", e)
            false
        }
    }

    fun canUseBiometricUnlock(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Biometric unlock availability check failed", e)
            false
        }
    }

    fun isSecureStorageAvailable(context: Context): Boolean = securePrefsOrNull(context) != null

    private fun handleFailedAttempt(
        prefs: SharedPreferences,
        attemptsUsed: Int
    ): PasscodeVerifyResult {
        return if (attemptsUsed >= MAX_FAILED_ATTEMPTS) {
            val lockedUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL_MS, lockedUntil)
                .apply()
            PasscodeVerifyResult.Locked(LOCKOUT_DURATION_MS)
        } else {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attemptsUsed).apply()
            PasscodeVerifyResult.Failed(
                attemptsRemaining = (MAX_FAILED_ATTEMPTS - attemptsUsed).coerceAtLeast(0),
                lockoutRemainingMs = 0L
            )
        }
    }

    private fun securePrefsOrNull(context: Context): SharedPreferences? {
        val appContext = context.applicationContext
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { error ->
            Log.w(TAG, "Encrypted preferences unavailable", error)
            null
        }
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hashPasscode(passcode: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val data = "$salt:$passcode".toByteArray(Charsets.UTF_8)
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        val leftBytes = left.toByteArray(Charsets.UTF_8)
        val rightBytes = right.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(leftBytes, rightBytes)
    }
}
