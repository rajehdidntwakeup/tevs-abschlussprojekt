package com.tevs.server.replication;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReplicationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public ReplicationPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${rabbitmq.exchange:status.sync}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public void publish(ReplicationEvent event) {
        rabbitTemplate.convertAndSend(exchange, "status.update", event);
    }
}
