package dev.UranophileLab.cryptafile.ui.settings

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import dev.UranophileLab.cryptafile.security.SecurityManager
import dev.UranophileLab.cryptafile.data.VaultRepository
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.UranophileLab.cryptafile.ui.theme.CryptaFileTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val repository = VaultRepository(applicationContext)

        setContent {
            var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", false)) }
            var useStealthMode by remember { mutableStateOf(prefs.getBoolean("stealth_mode", false)) }
            var currentTheme by remember { mutableStateOf(prefs.getString("app_theme", "System") ?: "System") }

            val darkTheme = when (currentTheme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CryptaFileTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onBackClick = { finish() },
                        useBiometric = useBiometric,
                        onBiometricToggle = { enabled ->
                            useBiometric = enabled
                            prefs.edit().putBoolean("use_biometric", enabled).apply()
                        },
                        useStealthMode = useStealthMode,
                        onStealthToggle = { enabled ->
                            useStealthMode = enabled
                            prefs.edit().putBoolean("stealth_mode", enabled).apply()
                            toggleStealthMode(enabled)
                        },
                        onChangePassword = { old, new ->
                            changeMasterPassword(repository, old, new)
                        },
                        onThemeClick = { theme ->
                            currentTheme = theme
                            prefs.edit().putString("app_theme", theme).apply()
                            applyTheme(theme)
                        },
                        onLanguageClick = { showLanguageDialog() },
                        onExportClick = {
                            lifecycleScope.launch {
                                val file = repository.exportVault()
                                if (file != null) {
                                    Toast.makeText(this@SettingsActivity, "Vault exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@SettingsActivity, "Vault is empty or export failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTrustManagementClick = {
                            startActivity(Intent(this@SettingsActivity, TrustManagementActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            "Light" -> AppCompatDelegate.MODE_NIGHT_NO
            "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Spanish", "French", "German")
        val codes = arrayOf("en", "es", "fr", "de")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(codes[which])
                AppCompatDelegate.setApplicationLocales(localeList)
                Toast.makeText(this, "Language set to ${languages[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun changeMasterPassword(repository: VaultRepository, old: String, new: String) {
        lifecycleScope.launch {
            val resolvedOld = SecurityManager.resolvePassphrase(applicationContext, old)
            if (resolvedOld == null) {
                Toast.makeText(this@SettingsActivity, "Incorrect old password or recovery code", Toast.LENGTH_LONG).show()
                return@launch
            }

            Toast.makeText(this@SettingsActivity, "Re-encrypting vault...", Toast.LENGTH_SHORT).show()
            val success = repository.reEncryptVault(resolvedOld, new)
            if (success) {
                SecurityManager.updateMasterPassword(applicationContext, new)
                Toast.makeText(this@SettingsActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SettingsActivity, "Failed to update password. Re-encryption failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleStealthMode(enabled: Boolean) {
        val pm = packageManager
        val defaultComponent = ComponentName(this, "dev.UranophileLab.cryptafile.MainActivityDefault")
        val calcComponent = ComponentName(this, "dev.UranophileLab.cryptafile.MainActivityCalculator")

        if (enabled) {
            pm.setComponentEnabledSetting(calcComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
            pm.setComponentEnabledSetting(defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        } else {
            pm.setComponentEnabledSetting(defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
            pm.setComponentEnabledSetting(calcComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        }
        Toast.makeText(this, "App will close to apply icon change", Toast.LENGTH_SHORT).show()
    }
}
