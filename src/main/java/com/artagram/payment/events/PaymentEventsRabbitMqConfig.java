package com.artagram.payment.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas propias ("payment.*"): el topic exchange reparte una copia
 * independiente del evento a cada cola que se le ligue, así que
 * artagram-payment puede sincronizar su propio estado de usuario sin
 * competir con las colas "users.*"/"canvas.*" de otros servicios.
 */
@Configuration
public class PaymentEventsRabbitMqConfig {

    public static final String EXCHANGE = "artagram.events";
    public static final String DLX_EXCHANGE = "artagram.dlx";

    public static final String QUEUE_USER_REGISTERED = "payment.user.registered";
    public static final String QUEUE_USER_DELETED = "payment.user.deleted";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue paymentUserRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_USER_REGISTERED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_USER_REGISTERED + ".dlq")
                .build();
    }

    @Bean
    public Binding paymentUserRegisteredBinding(Queue paymentUserRegisteredQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentUserRegisteredQueue).to(eventsExchange).with("user.registered");
    }

    @Bean
    public Queue paymentUserDeletedQueue() {
        return QueueBuilder.durable(QUEUE_USER_DELETED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_USER_DELETED + ".dlq")
                .build();
    }

    @Bean
    public Binding paymentUserDeletedBinding(Queue paymentUserDeletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentUserDeletedQueue).to(eventsExchange).with("user.deleted");
    }
}
