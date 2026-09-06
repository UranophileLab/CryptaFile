package dev.UranophileLab.cryptafile.ui.viewer

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.UranophileLab.cryptafile.NativeLib
import dev.UranophileLab.cryptafile.databinding.ActivitySecureViewerBinding
import dev.UranophileLab.cryptafile.security.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SecureViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecureViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecureViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra("file_path") ?: return
        val passphrase = intent.getStringExtra("passphrase") ?: return

        decryptAndView(filePath, passphrase)
    }

    private fun decryptAndView(path: String, passphrase: String) {
        binding.progressViewer.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val salt = NativeLib.readSaltNative(path) ?: return@withContext Result.failure(Exception("Invalid format"))
                val key = KeyManager.deriveKey(passphrase, salt)
                val tempDecrypted = File(cacheDir, "temp_view_" + System.currentTimeMillis())
                
                val success = NativeLib.decryptFileNative(path, tempDecrypted.absolutePath, key)
                if (success) Result.success(tempDecrypted) else Result.failure(Exception("Decryption failed"))
            }

            binding.progressViewer.visibility = View.GONE
            result.onSuccess { tempFile ->
                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                if (bitmap != null) {
                    binding.imgViewer.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@SecureViewerActivity, "Format not supported", Toast.LENGTH_SHORT).show()
                }
                tempFile.deleteOnExit()
            }.onFailure { 
                showError(it.message ?: "Error")
            }
        }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
