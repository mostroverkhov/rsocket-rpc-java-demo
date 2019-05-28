package io.rsocket.rpc.demo.service;

import io.rsocket.RSocketFactory;
import io.rsocket.rpc.demo.service.protobuf.*;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class Main {

    public static void main(String... args) {
        CloseableChannel server = RSocketFactory.receive()
                .acceptor((setup, senderRSocket) -> Mono.just(serverHandler()))
                .transport(TcpServerTransport.create("localhost", 5000))
                .start()
                .block();

        RSocketFactory.connect()
                .transport(TcpClientTransport.create(server.address()))
                .start()
                .flatMapMany(rSocket -> {

                            StreamServiceClient streamServiceClient = new StreamServiceClient(rSocket);
                            ChannelServiceClient channelServiceClient = new ChannelServiceClient(rSocket);

                            Instant now = Instant.now();
                            Request request = Request.newBuilder().setMessage(now.toString()).build();

                            Mono<Response> response = streamServiceClient
                                    .response(request)
                                    .doOnNext(r -> System.out.println(r.getMessage()));

                            Mono<Response> clientStream = streamServiceClient
                                    .clientStream(Mono.just(request))
                                    .doOnNext(r -> System.out.println(r.getMessage()));

                            Flux<Response> serverStream = streamServiceClient
                                    .serverStream(request)
                                    .doOnNext(r -> System.out.println(r.getMessage()));

                            Flux<Response> channel = channelServiceClient.
                                    channel(Mono.just(request))
                                    .doOnNext(r -> System.out.println(r.getMessage()));

                            return response.then(clientStream).thenMany(serverStream).thenMany(channel);

                        }
                ).repeatWhen(f -> f.delayElements(Duration.ofSeconds(1)))
                .take(Duration.ofSeconds(120))
                .doFinally(s -> server.dispose())
                .blockLast();
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
