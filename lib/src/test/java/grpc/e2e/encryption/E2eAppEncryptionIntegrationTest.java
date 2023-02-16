package grpc.e2e.encryption;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class E2eAppEncryptionIntegrationTest {

    private static final int SERVER_PORT;

    private static final String CLIENT_ID_A = "CLIENT_ID_A";
    private static final String CLIENT_ID_C = "CLIENT_ID_C";
    /**
     * The key to encrypt/decrypt **requests** from clientA and serverB.
     */
    private static Aead clientABRequestKey;
    /**
     * The key to encrypt/decrypt **responses** from serverB to clientA.
     */
    private static Aead clientABResponseKey;
    /**
     * A shared key to encrypt/decrypt both **requests** AND **responses** between clientC and serverB.
     * Note that this same key is used for twice to setting up client managed channel and in setting up server key maps.
     */
    private static Aead clientCBRequestKey;

    private static Server grpcServerB;

    private static ManagedChannel managedChannelAB;
    private static ManagedChannel managedChannelCB;
    private static ManagedChannel managedChannelWithNotMatchingKeys;
    private static ManagedChannel managedChannelABWithWrongClient;

    static {
        try {
            final ServerSocket severSocket = new ServerSocket(0);
            SERVER_PORT = severSocket.getLocalPort();
            severSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    public static void init() throws IOException {
        clientABRequestKey = initTink();
        clientABResponseKey = initTink();
        clientCBRequestKey = initTink();
        grpcServerB = startGrpcServer();
        final String grpcServerPath = "dns:///localhost:" + SERVER_PORT;
        managedChannelAB = NettyChannelBuilder.forTarget(grpcServerPath).usePlaintext()
            .intercept(
                new ClientE2eEncryptingInterceptor(clientABRequestKey, clientABResponseKey, CLIENT_ID_A)).build();
        managedChannelCB = NettyChannelBuilder.forTarget(grpcServerPath).usePlaintext()
            .intercept(
                new ClientE2eEncryptingInterceptor(clientCBRequestKey, clientCBRequestKey, CLIENT_ID_C)).build();
        //using valid keys and client id but client id does not matches to these keys. Expect request to fail.
        managedChannelWithNotMatchingKeys = NettyChannelBuilder.forTarget(grpcServerPath).usePlaintext()
            .intercept(
                new ClientE2eEncryptingInterceptor(clientABRequestKey, clientABResponseKey, CLIENT_ID_C)).build();
        managedChannelABWithWrongClient = NettyChannelBuilder.forTarget(grpcServerPath).usePlaintext()
            .intercept(
                new ClientE2eEncryptingInterceptor(clientABRequestKey, clientABResponseKey, "UNKNOWN_CLIENT")).build();
    }

    @AfterAll
    public static void shutdown() {
        grpcServerB.shutdown();
    }

    private static synchronized Aead initTink() {
        try {
            AeadConfig.register();
            KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM"));
            return handle.getPrimitive(Aead.class);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static Server startGrpcServer() throws IOException {
        final ImmutableMap<String, Aead> requestDecryptionKeys = ImmutableMap.of(CLIENT_ID_A, clientABRequestKey,
            CLIENT_ID_C, clientCBRequestKey);
        final ImmutableMap<String, Aead> responseEncryptionKeys = ImmutableMap.of(CLIENT_ID_A, clientABResponseKey,
            CLIENT_ID_C, clientCBRequestKey);
        return NettyServerBuilder.forPort(SERVER_PORT)
            .intercept(new ServerE2eAppEncryptionInterceptor())
            .addService(
                ServerInterceptors.intercept(
                    ServerInterceptors.useMarshalledMessages(new HelloServiceGrpcImpl().bindService(),
                        new ServerRequestDecryptor(requestDecryptionKeys),
                        new ServerResponseEncryptor(responseEncryptionKeys)
                    )
                )).build().start();
    }

    @Test
    public void testHappyPathAB() throws ExecutionException, InterruptedException, TimeoutException {
        testHappyPathOnChannel(managedChannelAB);
    }

    @Test
    public void testHappyPathCB() throws ExecutionException, InterruptedException, TimeoutException {
        testHappyPathOnChannel(managedChannelCB);
    }

    @Test
    public void testRequestFailsWhenUnknownClientPassed() throws ExecutionException, InterruptedException,
        TimeoutException {
        testRequestFailed(managedChannelABWithWrongClient);
    }

    @Test
    public void testRequestFailsNonMatchingClientPassed() throws ExecutionException, InterruptedException,
        TimeoutException {
        testRequestFailed(managedChannelWithNotMatchingKeys);
    }

    private void testRequestFailed(ManagedChannel managedChannelWithNotMatchingKeys) {
        final HelloServiceGrpc.HelloServiceFutureStub helloServiceFutureStub
            = HelloServiceGrpc.newFutureStub(managedChannelWithNotMatchingKeys);
        final String requestPayload = "request_payload" + System.nanoTime();
        final ListenableFuture<Encryptedservice.HelloResponse> responseFuture = helloServiceFutureStub.hello(
            Encryptedservice.HelloRequest.newBuilder().setRequestPayload(requestPayload).build());
        assertThrows(ExecutionException.class, () -> responseFuture.get(3, TimeUnit.SECONDS));
    }

    private void testHappyPathOnChannel(
        ManagedChannel managedChannelAB) throws InterruptedException, ExecutionException, TimeoutException {
        final HelloServiceGrpc.HelloServiceFutureStub helloServiceFutureStub
            = HelloServiceGrpc.newFutureStub(managedChannelAB);
        final String requestPayload = "request_payload" + System.nanoTime();
        final ListenableFuture<Encryptedservice.HelloResponse> responseFuture = helloServiceFutureStub.hello(
            Encryptedservice.HelloRequest.newBuilder().setRequestPayload(requestPayload).build());
        final Encryptedservice.HelloResponse response = responseFuture.get(3, TimeUnit.SECONDS);
        Assertions.assertEquals(Encryptedservice.HelloResponse.newBuilder().setResponsePayoload(
            "response payload" + requestPayload).build(), response);
    }

    private static class HelloServiceGrpcImpl extends HelloServiceGrpc.HelloServiceImplBase {

        @Override
        public void hello(Encryptedservice.HelloRequest request,
                          StreamObserver<Encryptedservice.HelloResponse> responseObserver) {
            responseObserver.onNext(
                Encryptedservice.HelloResponse.newBuilder().setResponsePayoload(
                    "response payload" + request.getRequestPayload()).build());
            responseObserver.onCompleted();
        }
    }

}
