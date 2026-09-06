package dev.UranophileLab.cryptafile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.UranophileLab.cryptafile.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustManagementScreen(
    trustedFiles: List<String>,
    onBackClick: () -> Unit,
    onRemoveTrust: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trust_management), fontWeight = FontWeight.Bold) },
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
        if (trustedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No trusted files yet", color = colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(trustedFiles) { path ->
                    val file = File(path)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = file.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(text = path, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemoveTrust(path) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Trust", tint = colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colorScheme.outlineVariant)
                }
            }
        }
    }
}
