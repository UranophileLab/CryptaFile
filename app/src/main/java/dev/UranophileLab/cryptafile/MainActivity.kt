package dev.UranophileLab.cryptafile

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.UranophileLab.cryptafile.data.VaultRepository
import dev.UranophileLab.cryptafile.security.BiometricHelper
import dev.UranophileLab.cryptafile.security.SecurityManager
import dev.UranophileLab.cryptafile.ui.CryptaFileHomeScreen
import dev.UranophileLab.cryptafile.ui.RecoveryDialog
import dev.UranophileLab.cryptafile.ui.VaultSetupUI
import dev.UranophileLab.cryptafile.ui.viewmodel.MainViewModel
import dev.UranophileLab.cryptafile.ui.viewmodel.MainViewModelFactory
import dev.UranophileLab.cryptafile.ui.viewer.SecureViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import dev.UranophileLab.cryptafile.ui.theme.CryptaFileTheme

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var pendingFiles by mutableStateOf<List<PendingFile>>(emptyList())
    private var isLocked by mutableStateOf(false)
    private var isFirstLaunch by mutableStateOf(false)
    private var showRecoveryDialog by mutableStateOf(false)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val explorerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val paths = result.data?.getStringArrayListExtra("selected_paths")
            paths?.let { list -> pendingFiles = list.map { PendingFile(it) } }
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            handlePickedPhotos(uris)
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Permission required for encryption", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        isFirstLaunch = !SecurityManager.isVaultSetup(applicationContext)

        val repository = VaultRepository(applicationContext)
        val factory = MainViewModelFactory(repository)

        setContent {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString("app_theme", "System")
            val darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CryptaFileTheme(darkTheme = darkTheme) {
                val viewModel: MainViewModel = viewModel(factory = factory)
                val vaultFiles = viewModel.vaultFiles

                LaunchedEffect(Unit) {
                    checkPermissions()
                }

                LaunchedEffect(viewModel.uiEvent) {
                    lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.uiEvent.collect { event ->
                                when (event) {
                                    is MainViewModel.UiEvent.ShowToast -> {
                                        Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isFirstLaunch) {
                        VaultSetupUI { pass, recovery ->
                            SecurityManager.setupVault(applicationContext, pass, recovery)
                            isFirstLaunch = false
                            checkBiometricLock()
                        }
                    } else if (isLocked) {
                        if (showRecoveryDialog) {
                            RecoveryDialog(
                                onDismiss = { showRecoveryDialog = false },
                                onRecover = { code ->
                                    val pass = SecurityManager.recoverPassword(applicationContext, code)
                                    if (pass != null) {
                                        Toast.makeText(this@MainActivity, "Password: $pass", Toast.LENGTH_LONG).show()
                                        showRecoveryDialog = false
                                        isLocked = false
                                    } else {
                                        Toast.makeText(this@MainActivity, "Invalid Recovery Code", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Locked", style = MaterialTheme.typography.headlineMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { checkBiometricLock() }) {
                                    Text("Unlock")
                                }
                                TextButton(onClick = { showRecoveryDialog = true }) {
                                    Text("Forgot Password?")
                                }
                            }
                        }
                    } else {
                        CryptaFileHomeScreen(
                            vaultFiles = vaultFiles,
                            onAddClick = { launchExplorer() },
                            onSettingsClick = {
                                startActivity(Intent(this@MainActivity, dev.UranophileLab.cryptafile.ui.settings.SettingsActivity::class.java))
                            },
                            pendingFiles = pendingFiles.map { it.path },
                            onActionSelected = { paths, pass, isEncrypt ->
                                val resolvedPass = SecurityManager.resolvePassphrase(applicationContext, pass)
                                if (resolvedPass != null) {
                                    val filesToProcess = pendingFiles.filter { it.path in paths }
                                    viewModel.executeBatchAction(filesToProcess, resolvedPass, isEncrypt)
                                } else {
                                    Toast.makeText(this@MainActivity, "Invalid Password or Recovery Code", Toast.LENGTH_SHORT).show()
                                }
                                pendingFiles = emptyList()
                            },
                            onCancelAction = {
                                pendingFiles = emptyList()
                            },
                            onDecryptClick = { file, pass ->
                                val resolvedPass = if (file.isTrusted && pass.isEmpty()) {
                                    SecurityManager.getMasterPassword(applicationContext)
                                } else {
                                    SecurityManager.resolvePassphrase(applicationContext, pass)
                                }
                                if (resolvedPass != null) {
                                    viewModel.decryptFile(file, resolvedPass)
                                } else {
                                    Toast.makeText(this@MainActivity, "Invalid Password or Recovery Code", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onViewClick = { file, pass ->
                                val resolvedPass = if (file.isTrusted && pass.isEmpty()) {
                                    SecurityManager.getMasterPassword(applicationContext)
                                } else {
                                    SecurityManager.resolvePassphrase(applicationContext, pass)
                                }
                                if (resolvedPass != null) {
                                    val intent = Intent(this@MainActivity, SecureViewerActivity::class.java).apply {
                                        putExtra("file_path", file.path)
                                        putExtra("passphrase", resolvedPass)
                                    }
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this@MainActivity, "Invalid Password or Recovery Code", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onTrustClick = { file ->
                                viewModel.toggleTrust(file)
                            },
                            onFileSettingsClick = { file ->
                                Toast.makeText(this@MainActivity, "Settings for ${file.name}", Toast.LENGTH_SHORT).show()
                            },
                            onGalleryClick = { launchGallery() }
                        )
                    }
                }

                // Check biometric if not first launch and not already locked
                DisposableEffect(Unit) {
                    if (!isFirstLaunch && !isLocked) {
                        checkBiometricLock()
                    }
                    onDispose {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            if (z < -8.0) {
                isLocked = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun launchExplorer() {
        explorerLauncher.launch(Intent(this, dev.UranophileLab.cryptafile.ui.explorer.FileExplorerActivity::class.java))
    }

    private fun launchGallery() {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun handlePickedPhotos(uris: List<Uri>) {
        lifecycleScope.launch {
            val pendingList = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val fileName = getFileName(uri) ?: "gallery_photo_${System.currentTimeMillis()}.jpg"
                            val tempFile = File(cacheDir, fileName)
                            FileOutputStream(tempFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            PendingFile(tempFile.absolutePath, uri)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            if (pendingList.isNotEmpty()) {
                pendingFiles = pendingList
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun checkBiometricLock() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val useBiometric = prefs.getBoolean("use_biometric", false)
        if (useBiometric && BiometricHelper.isAvailable(this)) {
            isLocked = true
            BiometricHelper.showPrompt(this) {
                isLocked = false
            }
        }
    }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            }
        }
    }
}
