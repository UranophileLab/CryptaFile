#include <jni.h>
#include <string>
#include <vector>
#include "SecurityManager.h"
#include "FileProcessor.h"
#include "VaultCore.h"

using namespace cryptafile;

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_UranophileLab_cryptafile_NativeLib_encryptFileNative(
        JNIEnv* env, jobject, jstring jIn, jstring jOut, jbyteArray jKey, jbyteArray jSalt) {
    const char* inPath = env->GetStringUTFChars(jIn, NULL);
    const char* outPath = env->GetStringUTFChars(jOut, NULL);
    jbyte* key = env->GetByteArrayElements(jKey, NULL);
    jbyte* salt = env->GetByteArrayElements(jSalt, NULL);

    bool res = FileProcessor::encryptProcess(inPath, outPath, (unsigned char*)key, (unsigned char*)salt);

    env->ReleaseStringUTFChars(jIn, inPath);
    env->ReleaseStringUTFChars(jOut, outPath);
    env->ReleaseByteArrayElements(jKey, key, JNI_ABORT);
    env->ReleaseByteArrayElements(jSalt, salt, JNI_ABORT);
    return res;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_UranophileLab_cryptafile_NativeLib_decryptFileNative(
        JNIEnv* env, jobject, jstring jIn, jstring jOut, jbyteArray jKey) {
    const char* inPath = env->GetStringUTFChars(jIn, NULL);
    const char* outPath = env->GetStringUTFChars(jOut, NULL);
    jbyte* key = env->GetByteArrayElements(jKey, NULL);

    bool res = FileProcessor::decryptProcess(inPath, outPath, (unsigned char*)key);

    env->ReleaseStringUTFChars(jIn, inPath);
    env->ReleaseStringUTFChars(jOut, outPath);
    env->ReleaseByteArrayElements(jKey, key, JNI_ABORT);
    return res;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_UranophileLab_cryptafile_NativeLib_readSaltNative(
        JNIEnv* env, jobject, jstring jIn) {
    const char* inPath = env->GetStringUTFChars(jIn, NULL);
    std::ifstream inFile(inPath, std::ios::binary);

    if (!inFile) {
        LOGE("Failed to open file for salt read: %s", inPath);
        env->ReleaseStringUTFChars(jIn, inPath);
        return NULL;
    }

    char magic[5];
    inFile.read(magic, 5);
    if (std::string(magic, 5) != CryptoConfig::MAGIC) {
        LOGE("Magic mismatch in salt read for file: %s", inPath);
        env->ReleaseStringUTFChars(jIn, inPath);
        return NULL;
    }

    unsigned char salt[CryptoConfig::SALT_SIZE];
    if (!inFile.read((char*)salt, CryptoConfig::SALT_SIZE)) {
        LOGE("Failed to read salt from file: %s", inPath);
        env->ReleaseStringUTFChars(jIn, inPath);
        return NULL;
    }

    env->ReleaseStringUTFChars(jIn, inPath);

    jbyteArray res = env->NewByteArray(CryptoConfig::SALT_SIZE);
    env->SetByteArrayRegion(res, 0, CryptoConfig::SALT_SIZE, (jbyte*)salt);
    return res;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_UranophileLab_cryptafile_NativeLib_hashStringNative(JNIEnv* env, jobject, jstring jInput) {
    const char* input = env->GetStringUTFChars(jInput, NULL);
    std::string hashed = SecurityManager::hashString(input);
    env->ReleaseStringUTFChars(jInput, input);
    return env->NewStringUTF(hashed.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_UranophileLab_cryptafile_NativeLib_resolvePassphraseNative(
        JNIEnv* env, jobject, jstring jInput, jstring jMasterPass, jstring jStoredHash) {

    const char* input = env->GetStringUTFChars(jInput, NULL);
    const char* masterPass = env->GetStringUTFChars(jMasterPass, NULL);
    const char* storedHash = env->GetStringUTFChars(jStoredHash, NULL);

    std::string result = SecurityManager::resolvePassphrase(input, masterPass, storedHash);

    env->ReleaseStringUTFChars(jInput, input);
    env->ReleaseStringUTFChars(jMasterPass, masterPass);
    env->ReleaseStringUTFChars(jStoredHash, storedHash);

    return result.empty() ? NULL : env->NewStringUTF(result.c_str());
}
