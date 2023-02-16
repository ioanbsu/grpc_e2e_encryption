package grpc.e2e.encryption;

import com.google.common.base.Strings;
import com.google.crypto.tink.Aead;
import io.grpc.MethodDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

/**
 * Server response encryptor. Encrypts pyre response bytes from server before sending response to a client.
 */
public class ServerResponseEncryptor implements MethodDescriptor.Marshaller<InputStream> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerRequestDecryptor.class);

    private final Map<String, Aead> responseEncryptionClientMap;

    public ServerResponseEncryptor(Map<String, Aead> responseEncryptionClientMap) {
        this.responseEncryptionClientMap = responseEncryptionClientMap;
    }

    @Override
    public InputStream stream(InputStream serializedProto) {
        final String clientIdFromContext = ServerE2eAppEncryptionInterceptor.CONTEXT_CLIENT_ID.get();
        if (Strings.isNullOrEmpty(clientIdFromContext)) {
            LOGGER.error(
                "There wasn't client id available on request side channel. Passing raw response to client without " +
                    "decrypting.");
            return serializedProto;
        }
        if (responseEncryptionClientMap.containsKey(clientIdFromContext)) {
            return TinkEncryptDecrypt.encrypt(serializedProto, responseEncryptionClientMap.get(clientIdFromContext));
        } else {
            LOGGER.error(
                "Provided client id == {} wasn't found in list of known keys to server. Passing data raw response to " +
                    "client without decrypting.", clientIdFromContext);
            return serializedProto;
        }
    }

    @Override
    public InputStream parse(InputStream stream) {
        return stream;
    }
}
