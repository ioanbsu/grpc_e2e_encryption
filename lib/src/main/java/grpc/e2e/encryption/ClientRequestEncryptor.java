package grpc.e2e.encryption;

import com.google.crypto.tink.Aead;
import io.grpc.MethodDescriptor;

import java.io.InputStream;

/**
 * Client request interceptor. Used to encrypt traffic from client to server.
 *
 * @param <T> the type of protobuf request object.
 */
public class ClientRequestEncryptor<T> implements MethodDescriptor.Marshaller<T> {

    private final MethodDescriptor.Marshaller<T> protosMarshaller;

    private final Aead requestEncryptor;

    public ClientRequestEncryptor(MethodDescriptor.Marshaller<T> protosMarshaller, Aead requestEncryptor) {
        this.protosMarshaller = protosMarshaller;
        this.requestEncryptor = requestEncryptor;
    }

    @Override
    public InputStream stream(T value) {
        return TinkEncryptDecrypt.encrypt(protosMarshaller.stream(value), requestEncryptor);
    }

    @Override
    public T parse(InputStream stream) {
        return protosMarshaller.parse(stream);
    }
}
