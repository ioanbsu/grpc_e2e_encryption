package grpc.e2e.encryption;

import static grpc.e2e.encryption.ServerE2eAppEncryptionInterceptor.E2E_ENCRYPTION_CLIENT_HEADER;

import com.google.crypto.tink.Aead;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Client encrypting interceptor.
 * Used to set up Encrypting and decrypting marshallers for requests and responses as well as to add Client
 * Interceptor that will inject client id to grpc side channel.
 */
public class ClientE2eEncryptingInterceptor implements ClientInterceptor {

    private final Aead encyptor;
    private final Aead decryptor;

    /**
     * Client id used to notify server of which key to use to decrypt incoming traffic
     */
    private final String clientId;

    public ClientE2eEncryptingInterceptor(final Aead encyptor, final Aead decryptor, final String clientId) {
        this.encyptor = encyptor;
        this.decryptor = decryptor;
        this.clientId = clientId;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method,
                                                               final CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(
                method.toBuilder(
                    new ClientRequestEncryptor<>(method.getRequestMarshaller(), encyptor),
                    new ClientResponseDecryptor<>(method.getResponseMarshaller(), decryptor)
                ).build(),
                callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (!headers.containsKey(E2E_ENCRYPTION_CLIENT_HEADER)) {
                    headers.put(E2E_ENCRYPTION_CLIENT_HEADER, clientId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
