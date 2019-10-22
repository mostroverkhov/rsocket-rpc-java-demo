package io.rsocket.rpc.demo.service;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ResourceLeakDetector;
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
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        final SslProvider sslProvider = sslProvider();
        InetSocketAddress address = new InetSocketAddress("localhost", 5000);

        SslContext clientSslContext = testClientContext(sslProvider);
        TcpClient client =
                TcpClient.create().doOnConnected(c -> {
                    c.addHandlerLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            System.out.println(cause);
                            super.exceptionCaught(ctx, cause);
                        }
                    });
                })
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

                    Flux<Response> response = channelServiceClient
                            .channel(Flux.just(request));

                    return Flux.interval(Duration.ofMillis(10)).onBackpressureDrop()
                            .flatMap(v -> response,1);
                        }
                ).repeatWhen(f -> f.delayElements(Duration.ofMillis(1000)))
                .take(Duration.ofSeconds(120))
                .doOnNext(r -> System.out.println(r.getMessage()))
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
