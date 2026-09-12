package dev.UranophileLab.cryptafile

import android.net.Uri

data class CryptFile(
    val name: String,
    val path: String,
    val size: String,
    val date: String,
    val isEncrypted: Boolean,
    val isTrusted: Boolean = false
)

data class PendingFile(
    val path: String,
    val originalUri: Uri? = null
)
