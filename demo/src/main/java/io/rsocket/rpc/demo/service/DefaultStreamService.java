package io.rsocket.rpc.demo.service;

import io.netty.buffer.ByteBuf;
import io.rsocket.rpc.demo.service.protobuf.Request;
import io.rsocket.rpc.demo.service.protobuf.Response;
import io.rsocket.rpc.demo.service.protobuf.StreamService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Service that returns a hello message. */
public class DefaultStreamService implements StreamService {

  @Override
  public Mono<Response> response(Request message, ByteBuf metadata) {
    return Mono.just(Response.newBuilder().setMessage("response - "+message.getMessage()).build());
  }

  @Override
  public Flux<Response> serverStream(Request message, ByteBuf metadata) {

    return Flux.interval(Duration.ofMillis(100)).onBackpressureDrop().map(v -> Response
            .newBuilder()
            .setMessage(String.format("server stream - %d %s", v, message.getMessage()))
            .build());
  }

  @Override
  public Mono<Response> clientStream(Publisher<Request> messages, ByteBuf metadata) {
    return Flux.from(messages).then(Mono.just(Response.newBuilder().setMessage("client stream").build()));
  }
}
