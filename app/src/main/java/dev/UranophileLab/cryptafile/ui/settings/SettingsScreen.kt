package dev.UranophileLab.cryptafile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.UranophileLab.cryptafile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    useBiometric: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    useStealthMode: Boolean,
    onStealthToggle: (Boolean) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onThemeClick: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onExportClick: () -> Unit,
    onTrustManagementClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.select_theme)) },
            text = {
                Column {
                    listOf("Light", "Dark", "System").forEach { theme ->
                        Text(
                            text = theme,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeClick(theme)
                                    showThemeDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("Old Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (oldPass.isNotEmpty() && newPass.isNotEmpty()) {
                        onChangePassword(oldPass, newPass)
                        showChangePasswordDialog = false
                        oldPass = ""
                        newPass = ""
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.primary,
                    navigationIconContentColor = colorScheme.primary
                )
            )
        },
        containerColor = colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item { SettingCategory(stringResource(R.string.security), colorScheme.primary) }
            
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.biometric_lock),
                    checked = useBiometric,
                    onCheckedChange = onBiometricToggle
                )
            }
            
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.stealth_mode),
                    checked = useStealthMode,
                    onCheckedChange = onStealthToggle
                )
            }

            item {
                SettingClickableItem(
                    title = stringResource(R.string.change_password),
                    onClick = { showChangePasswordDialog = true }
                )
            }

            item {
                SettingClickableItem(
                    title = stringResource(R.string.trust_management),
                    onClick = onTrustManagementClick
                )
            }

            item { SettingCategory(stringResource(R.string.appearance), colorScheme.primary) }
            
            item {
                SettingClickableItem(
                    title = stringResource(R.string.app_theme),
                    onClick = { showThemeDialog = true }
                )
            }
            
            item {
                SettingClickableItem(
                    title = stringResource(R.string.language),
                    onClick = onLanguageClick
                )
            }
            
            item {
                SettingClickableItem(
                    title = stringResource(R.string.export_vault),
                    onClick = onExportClick
                )
            }
        }
    }
}

@Composable
fun SettingCategory(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingSwitchItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingClickableItem(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 16.sp)
    }
}
