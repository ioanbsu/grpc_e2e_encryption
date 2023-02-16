# Grpc e2e app level encyrption

## Quickstart

To start a server with e2e encryption enabled do:

### Grpc server

```java
    private static Server startGrpcServer()throws IOException{
final ImmutableMap<String, Aead> requestDecryptionKeys=ImmutableMap.of(CLIENT_ID_A,clientABRequestKey);
final ImmutableMap<String, Aead> responseEncryptionKeys=ImmutableMap.of(CLIENT_ID_A,clientABResponseKey);
    return NettyServerBuilder.forPort(SERVER_PORT)
    .intercept(new ServerE2eAppEncryptionInterceptor())
    .addService(
    ServerInterceptors.intercept(
    ServerInterceptors.useMarshalledMessages(new HelloServiceGrpcImpl().bindService(),
    new ServerRequestDecryptor(requestDecryptionKeys),
    new ServerResponseEncryptor(responseEncryptionKeys)
    )
    )
    ).build().start();
    }
```

The `CLIENT_ID` is id of the client who will be talking to that server. Server can support several distinct clients,
each one with their own keys to be talking to it.
The `clientABRequestKey` is an `Aead` object that will be used by server to decrypt requests and encrypt responses. You
can choose to have separate `requestDecryptionKeys` and `responseEncryptionKeys` to use different keys for encrypting
responses and requests or you can use the same map if you are ok with encrypting and decrypting requests and responses
with the same key for the same client.

### Grpc Client

On client the setup is similar, except client doesn't have map of clients and keys. CLient only needs to know it's own
id and key(s) for encrypting requests and responses:

```java
 ManagedChannel managedChannelAB
    =NettyChannelBuilder.forTarget(grpcServerPath).usePlaintext()
    .intercept(new ClientE2eEncryptingInterceptor(clientABRequestKey,clientABResponseKey,CLIENT_ID_A)).build();
final HelloServiceGrpc.HelloServiceFutureStub helloServiceFutureStub
    =HelloServiceGrpc.newFutureStub(managedChannelAB);
    ...
```

The `E2eAppEncryptionIntegrationTest` contains code that starts server with e2e encryption and makes e2e encrypted call
against it.
To run test: `./gradlew :lib:test`. Tested with java 1.8 (corretto-1.8.0_362).

### Tink

Beware that in the `E2eAppEncryptionIntegrationTest` the `Aead` objects are generated on-a-fly. In real world you'd use
tink library to read keys from disk to initiate `Aead`.

## Caveats

Current code has overrides the `ServerInterceptors` and adds `intercept` method that supports separate encryption and
decryption marshallers.
The code to fix this in grpc.io is submitted and scheduled to be released in grpc.io v 1.54 by the ond of March 2023.
Once it is released, the `ServerInterceptors` from grpc.io shall be used instead.

## More info

See more info at this blog post.
