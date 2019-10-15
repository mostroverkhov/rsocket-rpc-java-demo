package io.rsocket.rpc.demo.service;

import io.netty.buffer.ByteBuf;
import io.rsocket.rpc.demo.service.protobuf.ChannelService;
import io.rsocket.rpc.demo.service.protobuf.Request;
import io.rsocket.rpc.demo.service.protobuf.Response;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class DefaultChannelService implements ChannelService {

    @Override
    public Flux<Response> channel(Publisher<Request> messages, ByteBuf metadata) {
        return Flux.from(messages).flatMap(m -> Flux.interval(Duration.ofMillis(100)).onBackpressureDrop()
        .map(v -> Response.newBuilder().setMessage("channel: " + m.getMessage()).build()));
    }
}
