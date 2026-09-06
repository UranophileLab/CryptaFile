#ifndef CRYPTAFILE_SECURITYMANAGER_H
#define CRYPTAFILE_SECURITYMANAGER_H

#include <string>
#include <openssl/sha.h>
#include <iomanip>
#include <sstream>

namespace cryptafile {

class SecurityManager {
public:
    /**
     * Replicates the SHA-256 hashing logic from Kotlin.
     */
    static std::string hashString(const std::string& input) {
        unsigned char hash[SHA256_DIGEST_LENGTH];
        SHA256((const unsigned char*)input.c_str(), input.length(), hash);

        std::stringstream ss;
        for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
            ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
        }
        return ss.str();
    }

    /**
     * Resolves the passphrase by checking against the master password
     * or verifying the recovery code hash.
     */
    static std::string resolvePassphrase(
        const std::string& input,
        const std::string& masterPassword,
        const std::string& storedRecoveryHash
    ) {
        // 1. Direct match with master password
        if (input == masterPassword) {
            return masterPassword;
        }

        // 2. Check if input is a valid recovery code by hashing it
        if (hashString(input) == storedRecoveryHash) {
            return masterPassword;
        }

        return ""; // Not resolved
    }
};

} // namespace cryptafile

#endif //CRYPTAFILE_SECURITYMANAGER_H
