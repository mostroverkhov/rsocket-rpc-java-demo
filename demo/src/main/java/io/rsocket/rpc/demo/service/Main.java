package io.rsocket.rpc.demo.service;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.rsocket.RSocketFactory;
import io.rsocket.rpc.demo.service.protobuf.*;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;

public class Main {

    public static void main(String... args) throws Exception {
        final SslProvider sslProvider = sslProvider();
        InetSocketAddress address = new InetSocketAddress("localhost", 5000);

        SslContext clientSslContext = testClientContext(sslProvider);
        TcpClient client =
                TcpClient.create()
                        .addressSupplier(() -> address)
                        .secure(spec -> spec.sslContext(clientSslContext));

        ClientTransport clientTransport = TcpClientTransport.create(client);

        RSocketFactory.connect()
                .transport(clientTransport)
                .start()
                .flatMapMany(rSocket -> {

                            StreamServiceClient streamServiceClient = new StreamServiceClient(rSocket);
                            ChannelServiceClient channelServiceClient = new ChannelServiceClient(rSocket);

                            Instant now = Instant.now();
                            Request request = Request.newBuilder().setMessage(now.toString()).build();

                    return Flux.interval(Duration.ofMillis(10)).onBackpressureDrop()
                            .flatMap(v -> streamServiceClient
                                    .response(request).mergeWith(streamServiceClient
                                                    .clientStream(Mono.just(request))).mergeWith(streamServiceClient
                                                                    .serverStream(request)).mergeWith(channelServiceClient.
                                                                                    channel(Mono.just(request))),256);
                        }
                ).repeatWhen(f -> f.delayElements(Duration.ofMillis(1000)))
                .take(Duration.ofSeconds(120))
                .blockLast();
    }

    private static SslContext testClientContext(SslProvider sslProvider) throws SSLException {
        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslProvider)
                .build();
    }

    private static SslProvider sslProvider() {
        final SslProvider sslProvider;
        if (OpenSsl.isAvailable()) {
            sslProvider = SslProvider.OPENSSL_REFCNT;
        } else {
            sslProvider = SslProvider.JDK;
        }
        return sslProvider;
    }
}
