package br.com.srm.credit.engine.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.srm.credit.engine.messaging.LiquidacaoMessageRecoverer;
import br.com.srm.credit.engine.service.LiquidacaoService;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.liquidacao.queue}")
    private String queue;

    @Value("${app.rabbitmq.liquidacao.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.liquidacao.routing-key}")
    private String routingKey;

    @Value("${app.rabbitmq.liquidacao.dlx}")
    private String deadLetterExchange;

    @Value("${app.rabbitmq.liquidacao.dlq}")
    private String deadLetterQueue;

    @Value("${app.rabbitmq.liquidacao.dlq-routing-key}")
    private String deadLetterRoutingKey;

    @Bean
    Queue liquidacaoQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    DirectExchange liquidacaoExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    Binding liquidacaoBinding(Queue liquidacaoQueue, DirectExchange liquidacaoExchange) {
        return BindingBuilder.bind(liquidacaoQueue).to(liquidacaoExchange).with(routingKey);
    }

    @Bean
    DirectExchange liquidacaoDeadLetterExchange() {
        return new DirectExchange(deadLetterExchange);
    }

    @Bean
    Queue liquidacaoDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    Binding liquidacaoDeadLetterBinding(Queue liquidacaoDeadLetterQueue, DirectExchange liquidacaoDeadLetterExchange) {
        return BindingBuilder.bind(liquidacaoDeadLetterQueue)
                .to(liquidacaoDeadLetterExchange)
                .with(deadLetterRoutingKey);
    }

    @Bean
    MessageRecoverer liquidacaoMessageRecoverer(
            RabbitTemplate rabbitTemplate, LiquidacaoService liquidacaoService, ObjectMapper objectMapper) {
        return new LiquidacaoMessageRecoverer(
                rabbitTemplate, deadLetterExchange, deadLetterRoutingKey, liquidacaoService, objectMapper);
    }

    @Bean
    MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
