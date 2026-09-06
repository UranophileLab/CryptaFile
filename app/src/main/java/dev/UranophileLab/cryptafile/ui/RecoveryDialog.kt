package dev.UranophileLab.cryptafile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecoveryDialog(
    onDismiss: () -> Unit,
    onRecover: (String) -> Unit
) {
    var recoveryCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover Password") },
        text = {
            Column {
                Text("Enter your 12-character recovery code to unlock the app and reveal your master password.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it },
                    label = { Text("Recovery Code") },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (recoveryCode.isNotEmpty()) onRecover(recoveryCode) }) {
                Text("Recover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
