package dev.UranophileLab.cryptafile.data

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File

object TrustManager {
    private const val TRUSTED_FILES_KEY = "trusted_files"
    private const val TAG = "TrustManager"

    private fun getNormalizedPath(path: String): String {
        return try {
            File(path).canonicalPath
        } catch (e: Exception) {
            path
        }
    }

    fun isTrusted(context: Context, path: String): Boolean {
        val normalizedPath = getNormalizedPath(path)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val trustedSet = prefs.getStringSet(TRUSTED_FILES_KEY, emptySet()) ?: emptySet()
        val result = trustedSet.contains(normalizedPath)
        Log.d(TAG, "isTrusted: $normalizedPath -> $result")
        return result
    }

    fun setTrusted(context: Context, path: String, isTrusted: Boolean) {
        val normalizedPath = getNormalizedPath(path)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val trustedSet = prefs.getStringSet(TRUSTED_FILES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (isTrusted) {
            trustedSet.add(normalizedPath)
        } else {
            trustedSet.remove(normalizedPath)
        }
        
        Log.d(TAG, "setTrusted: $normalizedPath -> $isTrusted. Total trusted: ${trustedSet.size}")
        prefs.edit().putStringSet(TRUSTED_FILES_KEY, trustedSet).apply()
    }

    fun getTrustedFiles(context: Context): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val result = prefs.getStringSet(TRUSTED_FILES_KEY, emptySet()) ?: emptySet()
        Log.d(TAG, "getTrustedFiles: ${result.size} files")
        return result
    }
}
