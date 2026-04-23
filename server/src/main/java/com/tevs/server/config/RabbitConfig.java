package com.tevs.server.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${node.id:node-a}")
    private String nodeId;

    @Bean
    public TopicExchange statusExchange() {
        return ExchangeBuilder.topicExchange("status.sync")
                .durable(true)
                .build();
    }

    @Bean
    public Queue statusQueue() {
        return QueueBuilder.durable("queue." + nodeId)
                .build();
    }

    @Bean
    public Binding statusBinding(Queue statusQueue, TopicExchange statusExchange) {
        return BindingBuilder.bind(statusQueue).to(statusExchange).with("status.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
