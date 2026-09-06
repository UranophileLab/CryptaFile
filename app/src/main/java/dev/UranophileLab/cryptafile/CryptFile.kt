package dev.UranophileLab.cryptafile

data class CryptFile(
    val name: String,
    val path: String,
    val size: String,
    val date: String,
    val isEncrypted: Boolean,
    val isTrusted: Boolean = false
)