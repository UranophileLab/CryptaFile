package dev.UranophileLab.cryptafile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.UranophileLab.cryptafile.data.VaultRepository
import dev.UranophileLab.cryptafile.security.KeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.os.Environment

@RunWith(AndroidJUnit4::class)
class VaultReEncryptionTest {

    private lateinit var repository: VaultRepository
    private lateinit var vaultPath: File

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = VaultRepository(context)
        vaultPath = File(Environment.getExternalStorageDirectory(), "CryptaFile/Vault")
        vaultPath.mkdirs()
        // Clear vault for test
        vaultPath.listFiles()?.forEach { 
            if (it.name.endsWith(".enc")) it.delete() 
        }
    }

    @Test
    fun testReEncryptionRecovery() {
        runBlocking {
            val oldPass = "old_password"
            val newPass = "new_password"
            
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            
            // 1. Create a file encrypted with old password
            val file1 = File(vaultPath, "test1.txt.enc")
            val salt1 = KeyManager.generateSalt()
            val key1 = KeyManager.deriveKey(oldPass, salt1)
            val temp1 = File(context.cacheDir, "temp1.txt")
            temp1.writeText("content1")
            NativeLib.encryptFileNative(temp1.absolutePath, file1.absolutePath, key1, salt1)
            temp1.delete()

            // 2. Create a file encrypted with new password (simulating partial success)
            val file2 = File(vaultPath, "test2.txt.enc")
            val salt2 = KeyManager.generateSalt()
            val key2 = KeyManager.deriveKey(newPass, salt2)
            val temp2 = File(context.cacheDir, "temp2.txt")
            temp2.writeText("content2")
            NativeLib.encryptFileNative(temp2.absolutePath, file2.absolutePath, key2, salt2)
            temp2.delete()

            // 3. Run re-encryption
            val success = repository.reEncryptVault(oldPass, newPass)
            
            // 4. Verify success
            assertTrue("Re-encryption should succeed by recovering file2", success)
            
            // 5. Verify both files can be decrypted with new password
            val salt1After = NativeLib.readSaltNative(file1.absolutePath)!!
            val key1After = KeyManager.deriveKey(newPass, salt1After)
            val dec1 = File(context.cacheDir, "dec1.txt")
            assertTrue(NativeLib.decryptFileNative(file1.absolutePath, dec1.absolutePath, key1After))
            assertTrue(dec1.readText() == "content1")
            
            val salt2After = NativeLib.readSaltNative(file2.absolutePath)!!
            val key2After = KeyManager.deriveKey(newPass, salt2After)
            val dec2 = File(context.cacheDir, "dec2.txt")
            assertTrue(NativeLib.decryptFileNative(file2.absolutePath, dec2.absolutePath, key2After))
            assertTrue(dec2.readText() == "content2")
            
            dec1.delete()
            dec2.delete()
            file1.delete()
            file2.delete()
        }
    }
}
