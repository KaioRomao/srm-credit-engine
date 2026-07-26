package br.com.srm.credit.engine.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LiquidacaoProducer {

    private static final Logger log = LoggerFactory.getLogger(LiquidacaoProducer.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.liquidacao.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.liquidacao.routing-key}")
    private String routingKey;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(LiquidacaoMensagem mensagem) {
        log.info(
                "Publicando liquidacao {} (trackId={}) na exchange '{}'",
                mensagem.liquidacaoId(),
                mensagem.trackId(),
                exchange);
        rabbitTemplate.convertAndSend(exchange, routingKey, mensagem);
    }
}
