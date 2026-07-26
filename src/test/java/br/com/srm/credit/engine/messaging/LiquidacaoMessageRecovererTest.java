package br.com.srm.credit.engine.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.RecursoNaoEncontradoException;
import br.com.srm.credit.engine.service.LiquidacaoService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidacaoMessageRecoverer")
class LiquidacaoMessageRecovererTest {

    private static final String DLX = "liquidacao.dlx";
    private static final String DLQ_ROUTING_KEY = "liquidacao.dlq";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private LiquidacaoService liquidacaoService;

    private LiquidacaoMessageRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new LiquidacaoMessageRecoverer(
                rabbitTemplate,
                DLX,
                DLQ_ROUTING_KEY,
                liquidacaoService,
                JsonMapper.builder().build());
    }

    @Test
    @DisplayName("deve registrar FALHA e republicar na DLQ quando as tentativas esgotam")
    void deveRegistrarFalhaERepublicarNaDlqQuandoTentativasEsgotam() {
        Message mensagem = mensagem("{\"liquidacaoId\":42,\"trackId\":\"11111111-2222-3333-4444-555555555555\"}");
        CambioException causa = new CambioException("sem cotação");

        recoverer.recover(mensagem, causa);

        verify(liquidacaoService).registrarFalha(42L, causa);
        verify(rabbitTemplate).send(eq(DLX), eq(DLQ_ROUTING_KEY), any(Message.class));
    }

    @Test
    @DisplayName("deve republicar na DLQ mesmo quando o registro de falha lança exceção")
    void deveRepublicarNaDlqMesmoQuandoRegistroDeFalhaLanca() {
        Message mensagem = mensagem("{\"liquidacaoId\":42,\"trackId\":\"11111111-2222-3333-4444-555555555555\"}");
        doThrow(new RecursoNaoEncontradoException("Liquidação não encontrada: 42"))
                .when(liquidacaoService)
                .registrarFalha(any(), any());

        assertThatCode(() -> recoverer.recover(mensagem, new CambioException("sem cotação")))
                .as("marcar FALHA é best-effort; a mensagem nunca pode ser descartada em silêncio")
                .doesNotThrowAnyException();

        verify(rabbitTemplate).send(eq(DLX), eq(DLQ_ROUTING_KEY), any(Message.class));
    }

    @Test
    @DisplayName("deve republicar na DLQ mesmo quando o payload é ilegível")
    void deveRepublicarNaDlqMesmoQuandoPayloadEhIlegivel() {
        Message mensagem = mensagem("isto nao e json");

        assertThatCode(() -> recoverer.recover(mensagem, new CambioException("qualquer")))
                .doesNotThrowAnyException();

        verify(liquidacaoService, never()).registrarFalha(any(), any());
        verify(rabbitTemplate).send(anyString(), anyString(), any(Message.class));
    }

    private static Message mensagem(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedExchange("liquidacao.exchange");
        properties.setReceivedRoutingKey("liquidacao.process");
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
