#ifndef CRYPTAFILE_FILEPROCESSOR_H
#define CRYPTAFILE_FILEPROCESSOR_H

#include <string>
#include <fstream>
#include <vector>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include "VaultCore.h"

// Define a logging macro that can be overridden
#ifndef LOGE
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CryptaFileNative", __VA_ARGS__)
#endif

namespace cryptafile {

class FileProcessor {
public:
    static bool encryptProcess(const std::string& inputPath, const std::string& outputPath, const unsigned char* key, const unsigned char* salt) {
        std::ifstream inFile(inputPath, std::ios::binary);
        std::ofstream outFile(outputPath, std::ios::binary);

        if (!inFile || !outFile) {
            LOGE("Failed to open files for encryption: in=%s, out=%s", inputPath.c_str(), outputPath.c_str());
            return false;
        }

        // Generate IV
        unsigned char iv[CryptoConfig::IV_SIZE];
        RAND_bytes(iv, CryptoConfig::IV_SIZE);

        // Write Header: MAGIC + SALT + IV
        outFile.write(CryptoConfig::MAGIC, 5);
        outFile.write((char*)salt, CryptoConfig::SALT_SIZE);
        outFile.write((char*)iv, CryptoConfig::IV_SIZE);

        // Placeholder for TAG (will seek back and write later)
        std::streampos tagPos = outFile.tellp();
        unsigned char dummyTag[CryptoConfig::TAG_SIZE] = {0};
        outFile.write((char*)dummyTag, CryptoConfig::TAG_SIZE);

        EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
        EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, CryptoConfig::IV_SIZE, NULL);
        EVP_EncryptInit_ex(ctx, NULL, NULL, key, iv);

        unsigned char inBuf[4096];
        unsigned char outBuf[4096 + 64];
        int outLen;

        while (inFile.read((char*)inBuf, sizeof(inBuf)) || inFile.gcount() > 0) {
            if (EVP_EncryptUpdate(ctx, outBuf, &outLen, (unsigned char*)inBuf, (int)inFile.gcount()) != 1) {
                LOGE("EVP_EncryptUpdate failed");
                EVP_CIPHER_CTX_free(ctx);
                return false;
            }
            outFile.write((char*)outBuf, outLen);
        }

        if (EVP_EncryptFinal_ex(ctx, outBuf, &outLen) != 1) {
            LOGE("EVP_EncryptFinal_ex failed");
            EVP_CIPHER_CTX_free(ctx);
            return false;
        }
        outFile.write((char*)outBuf, outLen);

        unsigned char tag[CryptoConfig::TAG_SIZE];
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, CryptoConfig::TAG_SIZE, tag);
        EVP_CIPHER_CTX_free(ctx);

        // Write the actual TAG
        outFile.seekp(tagPos);
        outFile.write((char*)tag, CryptoConfig::TAG_SIZE);

        return true;
    }

    static bool decryptProcess(const std::string& inputPath, const std::string& outputPath, const unsigned char* key) {
        std::ifstream inFile(inputPath, std::ios::binary);
        std::ofstream outFile(outputPath, std::ios::binary);

        if (!inFile || !outFile) {
            LOGE("Failed to open files: in=%s, out=%s", inputPath.c_str(), outputPath.c_str());
            return false;
        }

        char magic[5];
        inFile.read(magic, 5);
        if (std::string(magic, 5) != CryptoConfig::MAGIC) {
            LOGE("Magic mismatch");
            return false;
        }

        unsigned char salt[CryptoConfig::SALT_SIZE];
        unsigned char iv[CryptoConfig::IV_SIZE];
        unsigned char tag[CryptoConfig::TAG_SIZE];

        if (!inFile.read((char*)salt, CryptoConfig::SALT_SIZE) ||
            !inFile.read((char*)iv, CryptoConfig::IV_SIZE) ||
            !inFile.read((char*)tag, CryptoConfig::TAG_SIZE)) {
            LOGE("Failed to read header components");
            return false;
        }

        EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
        EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, CryptoConfig::IV_SIZE, NULL);
        EVP_DecryptInit_ex(ctx, NULL, NULL, key, iv);

        unsigned char inBuf[4096];
        unsigned char outBuf[4096 + 64];
        int outLen;

        while (inFile.read((char*)inBuf, sizeof(inBuf)) || inFile.gcount() > 0) {
            if (EVP_DecryptUpdate(ctx, outBuf, &outLen, (unsigned char*)inBuf, (int)inFile.gcount()) != 1) {
                LOGE("EVP_DecryptUpdate failed");
                EVP_CIPHER_CTX_free(ctx);
                return false;
            }
            outFile.write((char*)outBuf, outLen);
        }

        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, CryptoConfig::TAG_SIZE, tag);
        int ret = EVP_DecryptFinal_ex(ctx, outBuf, &outLen);
        EVP_CIPHER_CTX_free(ctx);

        if (ret > 0) {
            outFile.write((char*)outBuf, outLen);
            return true;
        } else {
            LOGE("EVP_DecryptFinal_ex failed (Auth tag mismatch?) ret=%d", ret);
            return false;
        }
    }
};

} // namespace cryptafile

#endif //CRYPTAFILE_FILEPROCESSOR_H
