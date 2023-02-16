package grpc.e2e.encryption;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Grpc server interceptor that will read the clien id from request and put it inside the request context so that
 * marshaller who does decryption later down the call stack knows what clint id to decrypt that request for.
 */
public class ServerE2eAppEncryptionInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CONTEXT_CLIENT_ID = Context.key("CLIENT_ID");
    public static final Metadata.Key<String> E2E_ENCRYPTION_CLIENT_HEADER = Metadata.Key.of("GrpcE2eClientId",
        Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 final ServerCallHandler<ReqT, RespT> next) {
        final Context context = Context.current()
            .withValue(CONTEXT_CLIENT_ID, headers.get(E2E_ENCRYPTION_CLIENT_HEADER));
        return Contexts.interceptCall(context, call, headers, next);
    }
}
