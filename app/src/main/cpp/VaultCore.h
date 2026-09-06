#ifndef CRYPTAFILE_VAULTCORE_H
#define CRYPTAFILE_VAULTCORE_H

#include <string>
#include <vector>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/err.h>

namespace cryptafile {

struct CryptoConfig {
    static constexpr int SALT_SIZE = 16;
    static constexpr int IV_SIZE = 12;
    static constexpr int TAG_SIZE = 16;
    static constexpr int KEY_SIZE = 32;
    static constexpr int PBKDF2_ITERATIONS = 65536;
    static const char* MAGIC;
};

const char* CryptoConfig::MAGIC = "CRYPT";

class VaultCore {
public:
    static std::vector<unsigned char> deriveKey(const std::string& passphrase, const std::vector<unsigned char>& salt) {
        std::vector<unsigned char> key(CryptoConfig::KEY_SIZE);
        if (PKCS5_PBKDF2_HMAC(passphrase.c_str(), passphrase.length(),
                              salt.data(), salt.size(),
                              CryptoConfig::PBKDF2_ITERATIONS,
                              EVP_sha256(),
                              CryptoConfig::KEY_SIZE, key.data()) != 1) {
            return {};
        }
        return key;
    }

    static std::vector<unsigned char> generateSalt() {
        std::vector<unsigned char> salt(CryptoConfig::SALT_SIZE);
        RAND_bytes(salt.data(), CryptoConfig::SALT_SIZE);
        return salt;
    }
};

} // namespace cryptafile

#endif //CRYPTAFILE_VAULTCORE_H
