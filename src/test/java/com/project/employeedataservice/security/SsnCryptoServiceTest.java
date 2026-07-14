package com.project.employeedataservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsnCryptoServiceTest {

    // Base64-encoded 32-byte key, test-only.
    private static final String TEST_ENC_KEY = "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcyExMTE=";
    private static final String TEST_HMAC_SECRET = "dGVzdC1obWFjLXNlY3JldC1rZXktZm9yLXVuaXQtdGVzdHM=";

    private SsnCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new SsnCryptoService(TEST_ENC_KEY, TEST_HMAC_SECRET);
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String ssn = "123-45-6789";

        String encrypted = cryptoService.encrypt(ssn);
        String decrypted = cryptoService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(ssn);
    }

    @Test
    void encrypt_neverProducesTheSameCiphertextTwice() {
        // Each call must use a fresh random IV, otherwise identical SSNs would
        // be trivially linkable by comparing ciphertexts.
        String ssn = "123-45-6789";

        String encryptedOnce = cryptoService.encrypt(ssn);
        String encryptedTwice = cryptoService.encrypt(ssn);

        assertThat(encryptedOnce).isNotEqualTo(encryptedTwice);
    }

    @Test
    void encrypt_neverContainsThePlaintextSsn() {
        String ssn = "123-45-6789";

        String encrypted = cryptoService.encrypt(ssn);

        assertThat(encrypted).doesNotContain(ssn);
    }

    @Test
    void hash_isDeterministicForTheSameInput() {
        String ssn = "123-45-6789";

        String hash1 = cryptoService.hash(ssn);
        String hash2 = cryptoService.hash(ssn);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_differsForDifferentInputs() {
        String hashA = cryptoService.hash("123-45-6789");
        String hashB = cryptoService.hash("123-45-6788");

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void hash_neverContainsThePlaintextSsn() {
        String ssn = "123-45-6789";

        String hash = cryptoService.hash(ssn);

        assertThat(hash).doesNotContain(ssn);
    }

    @Test
    void lastFourDigits_extractsFinalFourDigitsOnly() {
        assertThat(cryptoService.lastFourDigits("123-45-6789")).isEqualTo("6789");
    }

    @Test
    void mask_formatsAsConventionalMaskedSsn() {
        assertThat(cryptoService.mask("6789")).isEqualTo("***-**-6789");
    }
}
