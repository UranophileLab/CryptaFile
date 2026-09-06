package dev.UranophileLab.cryptafile.data

import android.content.Context
import android.os.Environment
import dev.UranophileLab.cryptafile.CryptFile
import dev.UranophileLab.cryptafile.NativeLib
import dev.UranophileLab.cryptafile.security.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

class VaultRepository(private val context: Context) {

    suspend fun processFile(path: String, passphrase: String, isEncrypt: Boolean): Boolean = withContext(Dispatchers.IO) {
        val inputFile = File(path)
        if (!inputFile.exists()) return@withContext false

        val vaultDir = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault").apply { mkdirs() }
        val outputFileName = if (isEncrypt) inputFile.name + ".enc" else inputFile.name.replace(".enc", "")
        val outputFile = File(vaultDir, outputFileName)

        val success = if (isEncrypt) {
            val salt = KeyManager.generateSalt()
            val key = KeyManager.deriveKey(passphrase, salt)
            NativeLib.encryptFileNative(inputFile.absolutePath, outputFile.absolutePath, key, salt)
        } else {
            val salt = NativeLib.readSaltNative(inputFile.absolutePath)
            if (salt != null) {
                val key = KeyManager.deriveKey(passphrase, salt)
                NativeLib.decryptFileNative(inputFile.absolutePath, outputFile.absolutePath, key)
            } else false
        }

        if (success) {
            shredFile(inputFile)
        }
        success
    }

    suspend fun reEncryptVault(oldPass: String, newPass: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault")
        if (!vaultDir.exists()) return@withContext true

        val encryptedFiles = vaultDir.listFiles { f -> f.name.endsWith(".enc") } ?: return@withContext true
        if (encryptedFiles.isEmpty()) return@withContext true

        val backupDir = File(context.cacheDir, "vault_backup_" + System.currentTimeMillis())
        backupDir.mkdirs()
        
        var allSuccess = true
        val processedFiles = mutableListOf<Pair<File, File>>() // Pair(Original, NewTemp)

        try {
            encryptedFiles.forEach { file ->
                val salt = NativeLib.readSaltNative(file.absolutePath)
                if (salt == null) {
                    allSuccess = false
                    return@forEach
                }

                val decryptedTemp = File(context.cacheDir, "dec_" + file.name)
                try {
                    val oldKey = KeyManager.deriveKey(oldPass, salt)
                    if (NativeLib.decryptFileNative(file.absolutePath, decryptedTemp.absolutePath, oldKey)) {
                        // Success with OLD pass. Re-encrypt with NEW pass.
                        val newSalt = KeyManager.generateSalt()
                        val newKey = KeyManager.deriveKey(newPass, newSalt)
                        val newEncFile = File(backupDir, file.name)
                        if (NativeLib.encryptFileNative(decryptedTemp.absolutePath, newEncFile.absolutePath, newKey, newSalt)) {
                            processedFiles.add(Pair(file, newEncFile))
                        } else {
                            allSuccess = false
                        }
                    } else {
                        // Failed with OLD pass. Try NEW pass (recovery from partial success).
                        val newKeyAttempt = KeyManager.deriveKey(newPass, salt)
                        if (NativeLib.decryptFileNative(file.absolutePath, decryptedTemp.absolutePath, newKeyAttempt)) {
                            // Already encrypted with NEW pass. Re-encrypt to be consistent (new salt).
                            val newSalt = KeyManager.generateSalt()
                            val newKey = KeyManager.deriveKey(newPass, newSalt)
                            val newEncFile = File(backupDir, file.name)
                            if (NativeLib.encryptFileNative(decryptedTemp.absolutePath, newEncFile.absolutePath, newKey, newSalt)) {
                                processedFiles.add(Pair(file, newEncFile))
                            } else {
                                allSuccess = false
                            }
                        } else {
                            allSuccess = false
                        }
                    }
                } finally {
                    if (decryptedTemp.exists()) decryptedTemp.delete()
                }

                if (!allSuccess) throw Exception("Failed during re-encryption of ${file.name}")
            }

            // If we reached here, all files are successfully re-encrypted in backupDir
            processedFiles.forEach { (original, newEnc) ->
                if (original.delete()) {
                    newEnc.copyTo(original, overwrite = true)
                } else {
                    allSuccess = false
                }
            }
            allSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            backupDir.deleteRecursively()
        }
    }

    suspend fun exportVault(): File? = withContext(Dispatchers.IO) {
        val vaultDir = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault")
        if (!vaultDir.exists()) return@withContext null

        val files = vaultDir.listFiles { f -> f.name.endsWith(".enc") } ?: return@withContext null
        if (files.isEmpty()) return@withContext null

        val zipFileName = "CryptaFile_Export_" + System.currentTimeMillis() + ".zip"
        val exportFile = File(context.cacheDir, zipFileName)
        
        try {
            java.util.zip.ZipOutputStream(exportFile.outputStream()).use { zipOut ->
                files.forEach { file ->
                    val zipEntry = java.util.zip.ZipEntry(file.name)
                    zipOut.putNextEntry(zipEntry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            
            // Move to public Downloads folder using MediaStore
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it).use { output ->
                        exportFile.inputStream().use { input -> input.copyTo(output!!) }
                    }
                    exportFile.delete()
                    return@withContext File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), zipFileName)
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val finalFile = File(downloadsDir, zipFileName)
                exportFile.copyTo(finalFile, overwrite = true)
                exportFile.delete()
                return@withContext finalFile
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun toggleTrust(path: String) {
        val isCurrentlyTrusted = TrustManager.isTrusted(context, path)
        TrustManager.setTrusted(context, path, !isCurrentlyTrusted)
    }

    suspend fun getRecentFiles(): List<CryptFile> = withContext(Dispatchers.IO) {
        val vaultDir = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault")
        if (!vaultDir.exists()) return@withContext emptyList<CryptFile>()

        val files = vaultDir.listFiles { file -> file.name.endsWith(".enc") } ?: emptyArray()
        files.sortedByDescending { it.lastModified() }.take(50).map { file ->
            CryptFile(
                name = file.name,
                path = file.absolutePath,
                size = "${file.length() / 1024} KB",
                date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                isEncrypted = true,
                isTrusted = TrustManager.isTrusted(context, file.absolutePath)
            )
        }
    }

    private fun shredFile(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            val raf = RandomAccessFile(file, "rws")
            val random = SecureRandom()
            val buffer = ByteArray(4096)
            var pos: Long = 0
            while (pos < length) {
                random.nextBytes(buffer)
                val toWrite = if (length - pos < buffer.size) (length - pos).toInt() else buffer.size
                raf.write(buffer, 0, toWrite)
                pos += toWrite
            }
            raf.close()
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
