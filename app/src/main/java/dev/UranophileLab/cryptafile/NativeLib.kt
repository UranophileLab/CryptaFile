package dev.UranophileLab.cryptafile

object NativeLib {
    init {
        System.loadLibrary("cryptafile")
    }

    external fun encryptFileNative(inputPath: String, outputPath: String, key: ByteArray, salt: ByteArray): Boolean
    external fun decryptFileNative(inputPath: String, outputPath: String, key: ByteArray): Boolean
    external fun readSaltNative(inputPath: String): ByteArray?
    external fun hashStringNative(input: String): String
    external fun resolvePassphraseNative(input: String, masterPass: String, storedHash: String): String?
}
