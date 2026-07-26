package br.com.srm.credit.engine.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.ConflitoNegocioException;
import br.com.srm.credit.engine.exception.LiquidacaoException;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.exception.RecursoNaoEncontradoException;
import br.com.srm.credit.engine.service.LiquidacaoService;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidacaoConsumer")
class LiquidacaoConsumerTest {

    private static final Long LIQUIDACAO_ID = 1L;
    private static final LiquidacaoMensagem MENSAGEM =
            new LiquidacaoMensagem(LIQUIDACAO_ID, UUID.fromString("11111111-2222-3333-4444-555555555555"));

    @Mock
    private LiquidacaoService liquidacaoService;

    @InjectMocks
    private LiquidacaoConsumer consumer;

    static Stream<Throwable> errosDeNegocio() {
        return Stream.of(
                new CambioException("Cotação não encontrada para BRL->USD"),
                new PrecificacaoException("Prazo inválido"),
                new LiquidacaoException("Precificação sem valor líquido"),
                new RecursoNaoEncontradoException("Liquidação não encontrada: 1"),
                new ConflitoNegocioException("Transição inválida"));
    }

    @Test
    @DisplayName("deve processar e finalizar na ordem correta quando a mensagem é válida")
    void deveProcessarEFinalizarNaOrdemCorretaQuandoMensagemEhValida() {
        consumer.consumir(MENSAGEM);

        var ordem = inOrder(liquidacaoService);
        ordem.verify(liquidacaoService).processaLiquidacao(LIQUIDACAO_ID);
        ordem.verify(liquidacaoService).finalizaLiquidacao(LIQUIDACAO_ID);
        verify(liquidacaoService, never()).registrarFalha(any(), any());
    }

    @ParameterizedTest(name = "deve registrar FALHA e confirmar quando a causa é {0}")
    @MethodSource("errosDeNegocio")
    void deveRegistrarFalhaEConfirmarQuandoCausaEhErroDeNegocio(Throwable causa) {
        doThrow(causa).when(liquidacaoService).finalizaLiquidacao(LIQUIDACAO_ID);

        assertThatCode(() -> consumer.consumir(MENSAGEM))
                .as("erro determinístico não deve relançar — retentar não muda o resultado")
                .doesNotThrowAnyException();

        verify(liquidacaoService).registrarFalha(LIQUIDACAO_ID, causa);
    }

    @Test
    @DisplayName("deve relançar a exceção quando a falha não é de negócio")
    void deveRelancarExcecaoQuandoFalhaNaoEhDeNegocio() {
        RuntimeException transitoria = new IllegalStateException("banco indisponível");
        doThrow(transitoria).when(liquidacaoService).processaLiquidacao(LIQUIDACAO_ID);

        assertThatThrownBy(() -> consumer.consumir(MENSAGEM))
                .as("falha potencialmente transitória deve subir para o retry agir")
                .isSameAs(transitoria);

        verify(liquidacaoService, never()).registrarFalha(any(), any());
    }

    @Test
    @DisplayName("deve relançar a exceção quando o registro de falha também falha")
    void deveRelancarExcecaoQuandoRegistroDeFalhaTambemFalha() {
        doThrow(new CambioException("sem cotação")).when(liquidacaoService).finalizaLiquidacao(LIQUIDACAO_ID);
        RecursoNaoEncontradoException aoRegistrar = new RecursoNaoEncontradoException("Liquidação não encontrada: 1");
        doThrow(aoRegistrar).when(liquidacaoService).registrarFalha(any(), any());

        assertThatThrownBy(() -> consumer.consumir(MENSAGEM))
                .as("sem DLQ isso viraria requeue infinito; com DLQ a mensagem é retida")
                .isSameAs(aoRegistrar);
    }

    @Test
    @DisplayName("deve interromper antes de finalizar quando o processamento falha")
    void deveInterromperAntesDeFinalizarQuandoProcessamentoFalha() {
        doThrow(new CambioException("erro no processamento"))
                .when(liquidacaoService)
                .processaLiquidacao(LIQUIDACAO_ID);

        consumer.consumir(MENSAGEM);

        verify(liquidacaoService, never()).finalizaLiquidacao(any());
    }
}
