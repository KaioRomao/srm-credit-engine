package br.com.srm.credit.engine.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidacaoProducer")
class LiquidacaoProducerTest {

    private static final String EXCHANGE = "liquidacao.exchange";
    private static final String ROUTING_KEY = "liquidacao.process";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private LiquidacaoProducer producer;

    @BeforeEach
    void setUp() {
        producer = new LiquidacaoProducer(rabbitTemplate);
        ReflectionTestUtils.setField(producer, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(producer, "routingKey", ROUTING_KEY);
    }

    @Test
    @DisplayName("deve publicar na exchange e routing key configuradas quando recebe o evento")
    void devePublicarNaExchangeERoutingKeyConfiguradasQuandoRecebeOEvento() {
        LiquidacaoMensagem mensagem = new LiquidacaoMensagem(1L, UUID.randomUUID());

        producer.publicar(mensagem);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, ROUTING_KEY, mensagem);
    }

    @Test
    @DisplayName("deve publicar somente após o commit da transação")
    void devePublicarSomenteAposOCommitDaTransacao() throws NoSuchMethodException {
        Method publicar = LiquidacaoProducer.class.getMethod("publicar", LiquidacaoMensagem.class);

        TransactionalEventListener listener = publicar.getAnnotation(TransactionalEventListener.class);

        assertThat(listener)
                .as("publicar antes do commit deixaria o consumidor ler registro inexistente")
                .isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
