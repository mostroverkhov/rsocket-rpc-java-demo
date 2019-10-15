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

public class Server {

    public static void main(String... args) throws Exception {
        final SslProvider sslProvider = sslProvider();
        InetSocketAddress address = new InetSocketAddress("localhost", 5000);

        SslContext serverSslContext = testServerContext(sslProvider);
        TcpServer server =
                TcpServer.create()
                        .addressSupplier(() -> address)
                        .secure(spec -> spec.sslContext(serverSslContext));

        TcpServerTransport tcpServerTransport = TcpServerTransport.create(server);
        CloseableChannel closeableChannel = RSocketFactory.receive()
                .acceptor((setup, senderRSocket) -> Mono.just(serverHandler()))
                .transport(tcpServerTransport)
                .start()
                .block();

        closeableChannel.onClose().block();
    }

    private static SslContext testServerContext(SslProvider sslProvider) throws CertificateException, SSLException {
        SecureRandom random = new SecureRandom();
        SelfSignedCertificate ssc = new SelfSignedCertificate("netifi.com", random, 1024);
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
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

    private static RequestHandlingRSocket serverHandler() {
        return new RequestHandlingRSocket(
                new StreamServiceServer(
                        new DefaultStreamService(),
                        Optional.empty(),
                        Optional.empty()),
                new ChannelServiceServer(
                        new DefaultChannelService(),
                        Optional.empty(),
                        Optional.empty()));
    }
}
