package dev.UranophileLab.cryptafile.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatActivity
import dev.UranophileLab.cryptafile.data.TrustManager
import dev.UranophileLab.cryptafile.ui.theme.CryptaFileTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.preference.PreferenceManager

class TrustManagementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString("app_theme", "System")
            val darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            var trustedFiles by remember { mutableStateOf(TrustManager.getTrustedFiles(this@TrustManagementActivity).toList()) }

            CryptaFileTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrustManagementScreen(
                        trustedFiles = trustedFiles,
                        onBackClick = { finish() },
                        onRemoveTrust = { path ->
                            TrustManager.setTrusted(this@TrustManagementActivity, path, false)
                            trustedFiles = TrustManager.getTrustedFiles(this@TrustManagementActivity).toList()
                        }
                    )
                }
            }
        }
    }
}
