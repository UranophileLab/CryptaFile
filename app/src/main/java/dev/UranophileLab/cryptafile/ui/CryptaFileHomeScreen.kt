package dev.UranophileLab.cryptafile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.UranophileLab.cryptafile.CryptFile
import dev.UranophileLab.cryptafile.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptaFileHomeScreen(
    vaultFiles: List<CryptFile> = emptyList(),
    onAddClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    pendingFiles: List<String> = emptyList(),
    onActionSelected: (List<String>, String, Boolean) -> Unit = { _, _, _ -> },
    onCancelAction: () -> Unit = {},
    onDecryptClick: (CryptFile, String) -> Unit = { _, _ -> },
    onViewClick: (CryptFile, String) -> Unit = { _, _ -> },
    onTrustClick: (CryptFile) -> Unit = {},
    onFileSettingsClick: (CryptFile) -> Unit = {},
    onGalleryClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    
    var showActionDialog by remember(pendingFiles) { mutableStateOf(pendingFiles.isNotEmpty()) }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var isEncryptMode by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }

    var selectedVaultFile by remember { mutableStateOf<CryptFile?>(null) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockPassphrase by remember { mutableStateOf("") }

    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = { 
                showActionDialog = false
                onCancelAction()
            },
            title = { Text("Action Required") },
            text = { Text("Selected ${pendingFiles.size} files. Choose action:") },
            confirmButton = {
                Button(onClick = {
                    isEncryptMode = true
                    showActionDialog = false
                    showPassphraseDialog = true
                }) { Text("Encrypt") }
            },
            dismissButton = {
                Button(onClick = {
                    isEncryptMode = false
                    showActionDialog = false
                    showPassphraseDialog = true
                }) { Text("Decrypt") }
            }
        )
    }

    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = { Text(if (isEncryptMode) "Encrypt" else "Decrypt") },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.passphrase_hint)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (passphrase.isNotEmpty()) {
                        onActionSelected(pendingFiles, passphrase, isEncryptMode)
                        showPassphraseDialog = false
                        passphrase = ""
                    }
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUnlockDialog && selectedVaultFile != null) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("Unlock File") },
            text = {
                Column {
                    if (selectedVaultFile!!.isTrusted) {
                        Text("This file is Trusted. You can unlock it without a password.", color = colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = unlockPassphrase,
                        onValueChange = { unlockPassphrase = it },
                        label = { Text(stringResource(R.string.passphrase_hint)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (unlockPassphrase.isNotEmpty() || selectedVaultFile?.isTrusted == true) {
                        onViewClick(selectedVaultFile!!, unlockPassphrase)
                        showUnlockDialog = false
                        unlockPassphrase = ""
                    }
                }) { Text("View") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (unlockPassphrase.isNotEmpty() || selectedVaultFile?.isTrusted == true) {
                        onDecryptClick(selectedVaultFile!!, unlockPassphrase)
                        showUnlockDialog = false
                        unlockPassphrase = ""
                    }
                }) { Text("Decrypt") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CryptaFile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.gallery),
                            tint = colorScheme.primary
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings",
                            tint = colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                shape = FloatingActionButtonDefaults.shape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add and encrypt"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        contentColor = colorScheme.onBackground,
        containerColor = colorScheme.background
    ) { innerPadding ->
        if (vaultFiles.isEmpty()) {
            EmptyVaultUI(innerPadding, onAddClick)
        } else {
            VaultContentUI(
                innerPadding = innerPadding,
                vaultFiles = vaultFiles,
                onFileClick = { file ->
                    selectedVaultFile = file
                    showUnlockDialog = true
                },
                onTrustClick = onTrustClick,
                onFileSettingsClick = onFileSettingsClick
            )
        }
    }
}

@Composable
fun EmptyVaultUI(innerPadding: PaddingValues, onAddClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "CryptaFile Logo",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.empty_state_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.empty_state_desc),
            fontSize = 16.sp,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        TextButton(
            onClick = onAddClick,
            contentPadding = PaddingValues(16.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colorScheme.primary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ENCRYPT YOUR FIRST FILE ->",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VaultContentUI(
    innerPadding: PaddingValues,
    vaultFiles: List<CryptFile>,
    onFileClick: (CryptFile) -> Unit,
    onTrustClick: (CryptFile) -> Unit,
    onFileSettingsClick: (CryptFile) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        item {
            Text(
                text = "Secure Vault",
                color = colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(vaultFiles) { file ->
            VaultFileItem(
                file = file,
                onClick = { onFileClick(file) },
                onTrustClick = { onTrustClick(file) },
                onSettingsClick = { onFileSettingsClick(file) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun VaultFileItem(
    file: CryptFile,
    onClick: () -> Unit,
    onTrustClick: (CryptFile) -> Unit = {},
    onSettingsClick: (CryptFile) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.Visibility,
            contentDescription = null,
            tint = if (file.isEncrypted) colorScheme.secondary else colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${file.size} • ${file.date}${if (file.isTrusted) " • Trusted" else ""}",
                fontSize = 12.sp,
                color = if (file.isTrusted) colorScheme.secondary else colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (file.isTrusted) "Remove Trust" else "Trust") },
                    onClick = {
                        onTrustClick(file)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        onSettingsClick(file)
                        showMenu = false
                    }
                )
            }
        }
    }
}
