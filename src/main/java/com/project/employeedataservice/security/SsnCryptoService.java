package com.project.employeedataservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Centralizes all SSN cryptography so the rest of the codebase never touches
 * raw key material or a plaintext SSN more than once (at the point of intake).
 *
 * Two representations are produced for every SSN:
 *
 *  - encrypt(): AES-256-GCM, reversible. Used so that a legitimate, authorized
 *    process (e.g. generating a W-2, or a compliance export) could recover the
 *    original value. Every ciphertext uses a fresh random IV, which is
 *    prepended to the output so no IV needs to be stored separately.
 *
 *  - hash(): HMAC-SHA256 with a server-side secret key, deterministic. This is
 *    NOT used to display or recover the SSN - it exists purely so the database
 *    can enforce "one employee per SSN" via a unique constraint without ever
 *    decrypting anything to do the comparison. Using HMAC (keyed) instead of a
 *    plain SHA-256 hash prevents offline dictionary/brute-force attacks against
 *    the ~1 billion possible SSNs, since the attacker would also need the
 *    server's secret key.
 */
@Component
public class SsnCryptoService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hmacKey;

    public SsnCryptoService(
            @Value("${app.security.ssn.encryption-key}") String encodedEncryptionKey,
            @Value("${app.security.ssn.hmac-secret}") String encodedHmacSecret
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedEncryptionKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.security.ssn.encryption-key must decode to exactly 32 bytes for AES-256, got "
                            + keyBytes.length);
        }
        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
        this.hmacKey = new SecretKeySpec(Base64.getDecoder().decode(encodedHmacSecret), HMAC_ALGORITHM);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt SSN", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_LENGTH_BYTES, data.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt SSN", e);
        }
    }

    public String hash(String plaintext) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            byte[] result = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to hash SSN", e);
        }
    }

    public String lastFourDigits(String rawSsn) {
        String digitsOnly = rawSsn.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 4
                ? digitsOnly.substring(digitsOnly.length() - 4)
                : digitsOnly;
    }

    public String mask(String lastFourDigits) {
        return "***-**-" + lastFourDigits;
    }
}
