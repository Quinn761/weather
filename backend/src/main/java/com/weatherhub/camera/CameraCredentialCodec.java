package com.weatherhub.camera;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts device verification codes before database persistence. */
@Component
public class CameraCredentialCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public CameraCredentialCodec(
            @Value("${CAMERA_CREDENTIAL_SECRET:}") String configuredSecret,
            @Value("${JWT_SECRET:weatherhub-camera-dev-secret}") String jwtSecret
    ) {
        this.key = digest((configuredSecret == null || configuredSecret.isBlank()) ? jwtSecret : configuredSecret);
    }

    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt camera credential", ex);
        }
    }

    private static byte[] digest(String source) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize camera credential encryption", ex);
        }
    }
}
