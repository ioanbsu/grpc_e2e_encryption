package grpc.e2e.encryption;

import com.google.crypto.tink.Aead;
import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * Client response decryptor. Used to decrypt responses from server.
 * @param <T> the type of protobuf response object.
 */
public class ClientResponseDecryptor<T> implements MethodDescriptor.Marshaller<T> {
    private final MethodDescriptor.Marshaller<T> protoMarshaller;

    private final Aead decryptor;

    public ClientResponseDecryptor(MethodDescriptor.Marshaller<T> protoMarshaller, Aead decryptor) {
        this.protoMarshaller = protoMarshaller;
        this.decryptor = decryptor;
    }

    @Override
    public InputStream stream(T value) {
        return protoMarshaller.stream(value);
    }

    @Override
    public T parse(InputStream encryptedStream) {
        return protoMarshaller.parse(
            new ByteArrayInputStream(Objects.requireNonNull(TinkEncryptDecrypt.decryptToBytes(encryptedStream, decryptor))));
    }
}
