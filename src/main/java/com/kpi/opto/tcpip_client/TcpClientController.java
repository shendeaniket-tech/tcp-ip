package com.kpi.opto.tcpip_client;

import com.kpi.opto.tcpip_client.config.TcpClientProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;

@RestController
@RequestMapping("/api")
public class TcpClientController {

    private final TcpClientProperties tcpClientProperties;

    public TcpClientController(TcpClientProperties tcpClientProperties) {
        this.tcpClientProperties = tcpClientProperties;
    }

    @PostMapping("/send")
    public Mono<String> sendMessage(
            @RequestParam String msg,
            @RequestParam String clientName
    ) {
        return Mono.justOrEmpty(
                        tcpClientProperties.getClients().stream()
                                .filter(client -> client.getName().equalsIgnoreCase(clientName))
                                .findFirst()
                ).switchIfEmpty(Mono.error(new IllegalArgumentException("No TCP client config found for name: " + clientName)))
                .flatMap(client -> TcpClient.create()
                        .host(client.getHost())
                        .port(client.getPort())
                        .handle((inbound, outbound) ->
                                outbound.sendString(Mono.just(msg))
                                        .then()
                                        .thenMany(inbound.receive().asString())
                                        .next()
                                        .doOnNext(response -> System.out.printf(
                                                "Received from %s (%s:%d): %s%n",
                                                clientName, client.getHost(), client.getPort(), response))
                                        .thenReturn("Message sent to " + clientName + " and response received.").then()
                        )
                        .connect()
                        .flatMap(connection -> connection.onDispose().then(Mono.just("Done")))
                );
    }
}
