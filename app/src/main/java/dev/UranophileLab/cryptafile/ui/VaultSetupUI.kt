package dev.UranophileLab.cryptafile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VaultSetupUI(onSetupComplete: (String, String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var password by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf(generateRecoveryCode()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vault Setup", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Recovery Code (Save this!)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            Text(
                text = recoveryCode,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { if (password.isNotEmpty()) onSetupComplete(password, recoveryCode) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            Text("Create Vault")
        }
    }
}

private fun generateRecoveryCode(): String {
    val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..3).map { 
        (1..4).map { allowedChars.random() }.joinToString("") 
    }.joinToString("-")
}
