package dev.UranophileLab.cryptafile.ui.explorer

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

import dev.UranophileLab.cryptafile.ui.theme.CryptaFileTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class FileExplorerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "System")

        setContent {
            val darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CryptaFileTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
                    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
                    
                    val files = remember(currentDir) {
                        currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                    }

                    FileExplorerScreen(
                        currentDirName = if (currentDir == Environment.getExternalStorageDirectory()) "Internal Storage" else currentDir.name,
                        files = files,
                        selectedFiles = selectedFiles,
                        onBackClick = {
                            if (currentDir != Environment.getExternalStorageDirectory() && currentDir.parentFile != null) {
                                currentDir = currentDir.parentFile!!
                            } else {
                                finish()
                            }
                        },
                        onFileClick = { clickedFile ->
                            if (clickedFile.isDirectory) {
                                currentDir = clickedFile
                            } else {
                                selectedFiles = if (selectedFiles.contains(clickedFile)) {
                                    selectedFiles - clickedFile
                                } else {
                                    selectedFiles + clickedFile
                                }
                            }
                        },
                        onFileToggle = { file ->
                            selectedFiles = if (selectedFiles.contains(file)) {
                                selectedFiles - file
                            } else {
                                selectedFiles + file
                            }
                        },
                        onDoneClick = {
                            val intent = Intent().apply {
                                putStringArrayListExtra("selected_paths", ArrayList(selectedFiles.map { it.absolutePath }))
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
