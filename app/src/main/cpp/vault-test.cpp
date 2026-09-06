#include <iostream>
#include <vector>
#include <string>
#include <fstream>
#include <cassert>

// Mock logging for standalone test (overrides Android log)
#define LOGE(...) { printf("ERROR: "); printf(__VA_ARGS__); printf("\n"); }

#include "VaultCore.h"
#include "FileProcessor.h"

using namespace cryptafile;

/**
 * Standard test for encrypting and then decrypting a file.
 */
void test_encryption_decryption() {
    std::string testFile = "test.txt";
    std::string encFile = "test.enc";
    std::string decFile = "test.dec.txt";
    std::string content = "Hello, C++ encryption!";
    std::string pass = "my_secure_password";

    // 1. Create test file
    std::ofstream of(testFile);
    of << content;
    of.close();

    // 2. Derive key and salt
    auto salt = VaultCore::generateSalt();
    auto key = VaultCore::deriveKey(pass, salt);

    // 3. Encrypt
    assert(FileProcessor::encryptProcess(testFile, encFile, key.data(), salt.data()));

    // 4. Decrypt
    assert(FileProcessor::decryptProcess(encFile, decFile, key.data()));

    // 5. Verify content
    std::ifstream inf(decFile);
    std::string readContent((std::istreambuf_iterator<char>(inf)), std::istreambuf_iterator<char>());
    assert(readContent == content);

    // Cleanup
    remove(testFile.c_str());
    remove(encFile.c_str());
    remove(decFile.c_str());

    std::cout << "[SUCCESS] test_encryption_decryption" << std::endl;
}

/**
 * Tests the recovery logic: recovering from a state where some files
 * are already updated to a new password.
 */
void test_recovery_logic() {
    std::string oldPass = "old_pass";
    std::string newPass = "new_pass";
    std::string content = "Secret data";

    std::string file1 = "file1.enc";
    std::string file2 = "file2.enc"; // This one will be "already updated"

    // 1. Prepare file1 (encrypted with old_pass)
    auto salt1 = VaultCore::generateSalt();
    auto key1 = VaultCore::deriveKey(oldPass, salt1);
    std::ofstream of1("temp1.txt"); of1 << content; of1.close();
    FileProcessor::encryptProcess("temp1.txt", file1, key1.data(), salt1.data());
    remove("temp1.txt");

    // 2. Prepare file2 (encrypted with new_pass - simulating partial failure recovery)
    auto salt2 = VaultCore::generateSalt();
    auto key2_new = VaultCore::deriveKey(newPass, salt2);
    std::ofstream of2("temp2.txt"); of2 << content; of2.close();
    FileProcessor::encryptProcess("temp2.txt", file2, key2_new.data(), salt2.data());
    remove("temp2.txt");

    // 3. Simulate Re-encryption loop logic (Logic from VaultRepository.kt)
    std::vector<std::string> files = {file1, file2};
    for (const auto& f : files) {
        // Read salt (Equivalent to NativeLib.readSaltNative)
        std::ifstream inFile(f, std::ios::binary);
        char magic[5]; inFile.read(magic, 5);
        unsigned char saltRead[16]; inFile.read((char*)saltRead, 16);
        inFile.close();
        std::vector<unsigned char> saltVec(saltRead, saltRead + 16);

        std::string tempDec = f + ".tmp.dec";
        bool reEncrypted = false;

        // Step A: Try old pass
        auto oldKey = VaultCore::deriveKey(oldPass, saltVec);
        if (FileProcessor::decryptProcess(f, tempDec, oldKey.data())) {
            // Success with OLD pass. Proceed with update.
            auto newSalt = VaultCore::generateSalt();
            auto newKey = VaultCore::deriveKey(newPass, newSalt);
            assert(FileProcessor::encryptProcess(tempDec, f + ".new", newKey.data(), newSalt.data()));
            reEncrypted = true;
        } else {
            // Step B: Try new pass (Recovery path)
            auto newKeyAttempt = VaultCore::deriveKey(newPass, saltVec);
            if (FileProcessor::decryptProcess(f, tempDec, newKeyAttempt.data())) {
                // Already updated! Just re-encrypt with fresh salt for consistency if needed,
                // or just leave it. The Java logic re-encrypts with a fresh salt.
                auto newSalt = VaultCore::generateSalt();
                auto newKey = VaultCore::deriveKey(newPass, newSalt);
                assert(FileProcessor::encryptProcess(tempDec, f + ".new", newKey.data(), newSalt.data()));
                reEncrypted = true;
            }
        }

        assert(reEncrypted && "Failed to decrypt/recover file");
        remove(tempDec.c_str());

        // Final Verification of the new file
        std::string finalDec = f + ".final.txt";
        auto finalSalt = VaultCore::generateSalt(); // Dummy, we need to read from file

        std::ifstream verifyIn(f + ".new", std::ios::binary);
        verifyIn.read(magic, 5);
        unsigned char vSalt[16]; verifyIn.read((char*)vSalt, 16);
        verifyIn.close();

        auto verifyKey = VaultCore::deriveKey(newPass, std::vector<unsigned char>(vSalt, vSalt+16));
        assert(FileProcessor::decryptProcess(f + ".new", finalDec, verifyKey.data()));

        remove((f + ".new").c_str());
        remove(finalDec.c_str());
        remove(f.c_str());
    }

    std::cout << "[SUCCESS] test_recovery_logic" << std::endl;
}

int main() {
    std::cout << "Starting C++ Vault Encryption Tests..." << std::endl;
    test_encryption_decryption();
    test_recovery_logic();
    std::cout << "--- ALL C++ TESTS PASSED ---" << std::endl;
    return 0;
}
