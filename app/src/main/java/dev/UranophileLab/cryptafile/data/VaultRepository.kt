package dev.UranophileLab.cryptafile.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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

    suspend fun processFile(path: String, passphrase: String, isEncrypt: Boolean, sourceUri: Uri? = null): Boolean = withContext(Dispatchers.IO) {
        val inputFile = File(path)
        if (!inputFile.exists()) return@withContext false

        val vaultDir = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault").apply { mkdirs() }
        val outputFileName = if (isEncrypt) inputFile.name + ".enc" else inputFile.name.replace(".enc", "")
        val outputFile = File(vaultDir, outputFileName)

        val success = if (isEncrypt) {
            val salt = KeyManager.generateSalt()
            val key = KeyManager.deriveKey(passphrase, salt)
            Log.d("VaultRepository", "Encrypting: ${inputFile.absolutePath} to ${outputFile.absolutePath}")
            val res = NativeLib.encryptFileNative(inputFile.absolutePath, outputFile.absolutePath, key, salt)
            Log.d("VaultRepository", "Encryption result: $res")
            res
        } else {
            val salt = NativeLib.readSaltNative(inputFile.absolutePath)
            if (salt != null) {
                val key = KeyManager.deriveKey(passphrase, salt)
                Log.d("VaultRepository", "Decrypting: ${inputFile.absolutePath} to ${outputFile.absolutePath}")
                val res = NativeLib.decryptFileNative(inputFile.absolutePath, outputFile.absolutePath, key)
                Log.d("VaultRepository", "Decryption result: $res")
                res
            } else {
                Log.e("VaultRepository", "Salt read failed for: ${inputFile.absolutePath}")
                false
            }
        }

        if (success) {
            Log.d("VaultRepository", "Processing success, shredding: ${inputFile.absolutePath}")
            shredFile(inputFile)
            if (isEncrypt && sourceUri != null) {
                Log.d("VaultRepository", "Deleting original URI: $sourceUri")
                deleteOriginalUri(sourceUri)
            }
        } else {
            Log.e("VaultRepository", "Processing failed for: ${inputFile.absolutePath}")
        }
        success
    }

    private fun deleteOriginalUri(uri: Uri) {
        try {
            val deletedCount = context.contentResolver.delete(uri, null, null)
            Log.d("VaultRepository", "Deleted URI count: $deletedCount")
        } catch (e: Exception) {
            Log.e("VaultRepository", "Failed to delete URI: $uri", e)
            if (e is IllegalArgumentException && e.message?.contains("Volume picker") == true) {
                tryDeleteFromMediaStore(uri)
            }
        }
    }

    private fun tryDeleteFromMediaStore(pickerUri: Uri) {
        try {
            var name: String? = null
            var size: Long = -1
            context.contentResolver.query(pickerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }

            if (name != null) {
                Log.d("VaultRepository", "Attempting MediaStore search for: $name ($size bytes)")
                val selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.SIZE + "=?"
                val selectionArgs = arrayOf(name, size.toString())
                
                // Try Images
                var count = context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                if (count > 0) {
                    Log.d("VaultRepository", "Successfully deleted from MediaStore Images: $name")
                    return
                }
                
                // Try Video
                count = context.contentResolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                if (count > 0) {
                    Log.d("VaultRepository", "Successfully deleted from MediaStore Video: $name")
                    return
                }
                
                // Try Downloads/Files (API 29+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    count = context.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                    Log.d("VaultRepository", "MediaStore deletion count for $name: $count")
                }
            }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Failed to tryDeleteFromMediaStore", e)
        }
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
                try {
                    newEnc.copyTo(original, overwrite = true)
                } catch (e: Exception) {
                    e.printStackTrace()
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
        if (!file.exists()) {
            Log.w("VaultRepository", "Shred failed: file does not exist ${file.absolutePath}")
            return
        }
        val originalPath = file.absolutePath
        try {
            val length = file.length()
            val random = SecureRandom()
            val buffer = ByteArray(4096)
            RandomAccessFile(file, "rws").use { raf ->
                var pos: Long = 0
                while (pos < length) {
                    random.nextBytes(buffer)
                    val toWrite = if (length - pos < buffer.size) (length - pos).toInt() else buffer.size
                    raf.write(buffer, 0, toWrite)
                    pos += toWrite
                }
                raf.fd.sync()
            }
            if (file.delete()) {
                Log.d("VaultRepository", "File deleted successfully: $originalPath")
                // Notify MediaStore that the file is gone
                MediaScannerConnection.scanFile(context, arrayOf(originalPath), null) { path, uri ->
                    Log.d("VaultRepository", "Media scan completed for $path, uri: $uri")
                }
            } else {
                Log.e("VaultRepository", "Failed to delete file after shredding: $originalPath. Trying MediaStore...")
                val deletedCount = context.contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    MediaStore.Files.FileColumns.DATA + "=?",
                    arrayOf(originalPath)
                )
                Log.d("VaultRepository", "MediaStore deletion count for $originalPath: $deletedCount")
            }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error shredding file: $originalPath", e)
        }
    }
}
