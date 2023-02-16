package grpc.e2e.encryption;

import com.google.common.base.Strings;
import com.google.crypto.tink.Aead;
import io.grpc.MethodDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

/**
 * Grpc server decryptor. For given client id (that is read from request context) decrypts bytes in input stream and
 * passes them down to the call stack.
 */
public class ServerRequestDecryptor implements MethodDescriptor.Marshaller<InputStream> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerRequestDecryptor.class);
    private final Map<String, Aead> requestDecryptorClientMap;

    public ServerRequestDecryptor(Map<String, Aead> requestDecryptorClientMap) {
        this.requestDecryptorClientMap = requestDecryptorClientMap;
    }

    @Override
    public InputStream stream(InputStream encryptedStream) {
        final String clientIdFromContext = ServerE2eAppEncryptionInterceptor.CONTEXT_CLIENT_ID.get();
        if (Strings.isNullOrEmpty(clientIdFromContext)) {
            LOGGER.error(
                "There wasn't client id available on request side channel. Passing data down to server without " +
                    "decrypting.");
            return encryptedStream;
        }
        if (requestDecryptorClientMap.containsKey(clientIdFromContext)) {
            return TinkEncryptDecrypt.decrypt(encryptedStream, requestDecryptorClientMap.get(clientIdFromContext));
        } else {
            LOGGER.error(
                "Provided client id == {} wasn't found in list of known keys to server. Passing data down to server " +
                    "without " +
                    "decrypting.", clientIdFromContext);
            return encryptedStream;
        }

    }

    @Override
    public InputStream parse(InputStream stream) {
        return stream;
    }
}
