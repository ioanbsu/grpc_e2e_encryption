package grpc.e2e.encryption;

import com.google.common.io.ByteStreams;
import com.google.crypto.tink.Aead;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/**
 * Utility class to encrypt/decrypt input streams/bytes.
 */
public final class TinkEncryptDecrypt {

    private TinkEncryptDecrypt() {

    }

    public static InputStream encrypt(final InputStream inputStream, final Aead encryptor) {
        try {
            final byte[] encrypted = encryptor.encrypt(ByteStreams.toByteArray(inputStream), null);
            return new ByteArrayInputStream(encrypted);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("The encryption of the payload inside input stream failed", e);
        }
    }

    public static InputStream decrypt(final InputStream inputStream, final Aead decryptor) {
        try {
            final byte[] decrypted = decryptor.decrypt(ByteStreams.toByteArray(inputStream), null);
            return new ByteArrayInputStream(decrypted);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("The decryption of the payload inside input stream failed", e);
        }
    }

    public static byte[] decryptToBytes(final InputStream inputStream, final Aead decryptor) {
        try {
            return decryptor.decrypt(ByteStreams.toByteArray(inputStream), null);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("The decryption of the payload inside input stream failed", e);
        }
    }

}
