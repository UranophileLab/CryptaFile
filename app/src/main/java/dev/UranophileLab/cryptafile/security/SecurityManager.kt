package dev.UranophileLab.cryptafile.security

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.UranophileLab.cryptafile.NativeLib

object SecurityManager {

    private const val PREFS_NAME = "secure_vault_prefs"
    private const val KEY_MASTER_PASSWORD = "saved_master_password"
    private const val KEY_RECOVERY_HASH = "recovery_code_hash"

    private var cachedPrefs: android.content.SharedPreferences? = null

    // 1. Initialize EncryptedSharedPreferences
    @Synchronized
    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        cachedPrefs?.let { return it }

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        cachedPrefs = prefs
        return prefs
    }

    // 2. Hash the recovery code using SHA-256 (Never store raw recovery codes)
    private fun hashString(input: String): String {
        return NativeLib.hashStringNative(input)
    }

    fun setupVault(context: Context, masterPassword: String, recoveryCode: String) {
        val prefs = getEncryptedPrefs(context)
        val hashedRecoveryCode = hashString(recoveryCode)

        prefs.edit(commit = true) {
            putString(KEY_MASTER_PASSWORD, masterPassword)
            putString(KEY_RECOVERY_HASH, hashedRecoveryCode)
        }
    }

    fun updateMasterPassword(context: Context, newPassword: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit(commit = true) {
            putString(KEY_MASTER_PASSWORD, newPassword)
        }
    }

    /**
     * Call this when the user forgets their password and enters their recovery code.
     * Returns the master password if the code matches, or null if it's incorrect.
     */
    fun recoverPassword(context: Context, inputRecoveryCode: String): String? {
        val prefs = getEncryptedPrefs(context)
        val storedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return null
        
        val inputHash = hashString(inputRecoveryCode)

        return if (inputHash == storedHash) {
            // Code matches! Return the securely stored master password.
            prefs.getString(KEY_MASTER_PASSWORD, null)
        } else {
            // Incorrect recovery code.
            null
        }
    }

    fun isVaultSetup(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains(KEY_MASTER_PASSWORD)
    }

    fun getMasterPassword(context: Context): String? {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_MASTER_PASSWORD, null)
    }

    /**
     * Checks if the input is either the master password or a valid recovery code.
     * Returns the actual master password to be used for decryption.
     */
    fun resolvePassphrase(context: Context, input: String): String? {
        val prefs = getEncryptedPrefs(context)
        val masterPass = prefs.getString(KEY_MASTER_PASSWORD, null) ?: return null
        val storedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return null

        return NativeLib.resolvePassphraseNative(input, masterPass, storedHash)
    }
}
