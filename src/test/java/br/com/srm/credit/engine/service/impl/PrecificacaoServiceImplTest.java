package br.com.srm.credit.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.srm.credit.engine.dto.rq.SimulacaoPrecificacaoRQ;
import br.com.srm.credit.engine.dto.rs.PrecificacaoRS;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.service.CambioService;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrecificacaoServiceImpl")
class PrecificacaoServiceImplTest {

    private static final BigDecimal TAXA_BASE = new BigDecimal("0.01");
    private static final String DUPLICATA = "DUPLICATA_MERCANTIL";
    private static final String CHEQUE = "CHEQUE_PRE_DATADO";

    @Mock
    private CambioService cambioService;

    @Mock
    private PrecificacaoRepository precificacaoRepository;

    @Mock
    private MoedaRepository moedaRepository;

    @InjectMocks
    private PrecificacaoServiceImpl precificacaoService;

    @Nested
    @DisplayName("calcular")
    class Calcular {

        @Test
        @DisplayName("deve aplicar spread de duplicata quando o tipo é DUPLICATA_MERCANTIL")
        void deveAplicarSpreadDeDuplicataQuandoTipoEhDuplicata() {
            Recebivel recebivel = recebivel(DUPLICATA, new BigDecimal("10000.00"), 87);

            BigDecimal vlLiquido = precificacaoService.calcular(recebivel, TAXA_BASE);

            assertThat(vlLiquido).isEqualByComparingTo("9308.9520");
        }

        @Test
        @DisplayName("deve aplicar spread de cheque quando o tipo é CHEQUE_PRE_DATADO")
        void deveAplicarSpreadDeChequeQuandoTipoEhCheque() {
            Recebivel recebivel = recebivel(CHEQUE, new BigDecimal("10000.00"), 87);

            BigDecimal vlLiquido = precificacaoService.calcular(recebivel, TAXA_BASE);

            assertThat(vlLiquido).isEqualByComparingTo("9050.5086");
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o tipo não tem strategy")
        void deveLancarExcecaoQuandoTipoNaoTemStrategy() {
            Recebivel recebivel = recebivel("NOTA_PROMISSORIA", new BigDecimal("10000.00"), 87);

            assertThatThrownBy(() -> precificacaoService.calcular(recebivel, TAXA_BASE))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("Strategy");
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o vencimento não é futuro")
        void deveLancarExcecaoQuandoVencimentoNaoEhFuturo() {
            Recebivel recebivel = recebivel(DUPLICATA, new BigDecimal("10000.00"), 0);

            assertThatThrownBy(() -> precificacaoService.calcular(recebivel, TAXA_BASE))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("Prazo inválido");
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o vencimento está no passado")
        void deveLancarExcecaoQuandoVencimentoEstaNoPassado() {
            Recebivel recebivel = recebivel(DUPLICATA, new BigDecimal("10000.00"), -30);

            assertThatThrownBy(() -> precificacaoService.calcular(recebivel, TAXA_BASE))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("Prazo inválido");
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o recebível é nulo")
        void deveLancarExcecaoQuandoRecebivelEhNulo() {
            assertThatThrownBy(() -> precificacaoService.calcular(null, TAXA_BASE))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("incompleto");
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o valor de face é nulo")
        void deveLancarExcecaoQuandoValorDeFaceEhNulo() {
            Recebivel recebivel = recebivel(DUPLICATA, null, 87);

            assertThatThrownBy(() -> precificacaoService.calcular(recebivel, TAXA_BASE))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("incompleto");
        }
    }

    @Nested
    @DisplayName("precificar")
    class Precificar {

        @Test
        @DisplayName("deve congelar spread, taxa base e prazo quando persiste a precificação")
        void deveCongelarSpreadTaxaBaseEPrazoQuandoPersiste() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("10000.00"), 87, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Precificacao precificacao = precificacaoService.precificar(recebivel, TAXA_BASE, "BRL");

            assertThat(precificacao.getVlSpread()).isEqualByComparingTo("0.015");
            assertThat(precificacao.getVlTaxaBase()).isEqualByComparingTo(TAXA_BASE);
            assertThat(precificacao.getQtPrazoDia()).isEqualTo(87);
            assertThat(precificacao.getVlLiquido()).isEqualByComparingTo("9308.9520");
        }

        @Test
        @DisplayName("deve congelar spread de cheque quando o tipo é CHEQUE_PRE_DATADO")
        void deveCongelarSpreadDeChequeQuandoTipoEhCheque() {
            Recebivel recebivel = recebivelComMoeda(CHEQUE, new BigDecimal("10000.00"), 87, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Precificacao precificacao = precificacaoService.precificar(recebivel, TAXA_BASE, "BRL");

            assertThat(precificacao.getVlSpread()).isEqualByComparingTo("0.025");
        }

        @Test
        @DisplayName("deve tratar moeda de pagamento nula como a moeda do próprio título")
        void deveTratarMoedaDePagamentoNulaComoAMoedaDoTitulo() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("10000.00"), 87, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Precificacao precificacao = precificacaoService.precificar(recebivel, TAXA_BASE, null);

            assertThat(precificacao.getMoedaDestino()).isNull();
            verify(moedaRepository, never()).findBySgMoeda(any());
        }

        @Test
        @DisplayName("deve tratar moeda de pagamento vazia como a moeda do próprio título")
        void deveTratarMoedaDePagamentoVaziaComoAMoedaDoTitulo() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("10000.00"), 87, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Precificacao precificacao = precificacaoService.precificar(recebivel, TAXA_BASE, "   ");

            assertThat(precificacao.getMoedaDestino()).isNull();
        }

        @Test
        @DisplayName("deve gravar moeda destino e valor convertido quando é cross-currency")
        void deveGravarMoedaDestinoEValorConvertidoQuandoEhCrossCurrency() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("10000.00"), 87, "BRL");
            Moeda usd = moeda(2L, "USD");
            when(cambioService.converter(any(), eq("BRL"), eq("USD"))).thenReturn(new BigDecimal("1833.6000"));
            when(moedaRepository.findBySgMoeda("USD")).thenReturn(Optional.of(usd));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Precificacao precificacao = precificacaoService.precificar(recebivel, TAXA_BASE, "USD");

            assertThat(precificacao.getMoedaDestino()).isEqualTo(usd);
            assertThat(precificacao.getVlConvertido()).isEqualByComparingTo("1833.6000");
        }

        @Test
        @DisplayName("deve propagar CambioException quando a moeda destino não existe")
        void devePropagarExcecaoQuandoMoedaDestinoNaoExiste() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("10000.00"), 87, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("XYZ"))).thenReturn(BigDecimal.ONE);
            when(moedaRepository.findBySgMoeda("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> precificacaoService.precificar(recebivel, TAXA_BASE, "XYZ"))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("XYZ");

            verify(precificacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar PrecificacaoException quando o recebível não tem moeda de origem")
        void deveLancarExcecaoQuandoRecebivelNaoTemMoedaDeOrigem() {
            Recebivel recebivel = recebivel(DUPLICATA, new BigDecimal("10000.00"), 87);

            assertThatThrownBy(() -> precificacaoService.precificar(recebivel, TAXA_BASE, "BRL"))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("moeda de origem");
        }

        @Test
        @DisplayName("deve persistir exatamente uma precificação por chamada")
        void devePersistirExatamenteUmaPrecificacaoPorChamada() {
            Recebivel recebivel = recebivelComMoeda(DUPLICATA, new BigDecimal("5000.00"), 60, "BRL");
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(precificacaoRepository.save(any(Precificacao.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            precificacaoService.precificar(recebivel, TAXA_BASE, "BRL");

            ArgumentCaptor<Precificacao> captor = ArgumentCaptor.forClass(Precificacao.class);
            verify(precificacaoRepository).save(captor.capture());
            assertThat(captor.getValue().getRecebivel()).isSameAs(recebivel);
            assertThat(captor.getValue().getDtCriacao()).isNotNull();
        }
    }

    @Nested
    @DisplayName("simular")
    class Simular {

        @Test
        @DisplayName("deve devolver valor líquido e prazo sem persistir nada")
        void deveDevolverValorLiquidoEPrazoSemPersistirNada() {
            SimulacaoPrecificacaoRQ requisicao = new SimulacaoPrecificacaoRQ(
                    new BigDecimal("10000.00"), LocalDate.now().plusDays(87), DUPLICATA, "BRL", "BRL", TAXA_BASE);
            when(cambioService.converter(any(), eq("BRL"), eq("BRL")))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PrecificacaoRS resposta = precificacaoService.simular(requisicao);

            assertThat(resposta.vlFace()).isEqualByComparingTo("10000.00");
            assertThat(resposta.vlLiquido()).isEqualByComparingTo("9308.9520");
            assertThat(resposta.qtPrazoDia()).isEqualTo(87);
            assertThat(resposta.tipoRecebivel()).isEqualTo(DUPLICATA);
            verify(precificacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve devolver valor convertido quando a moeda de pagamento é diferente")
        void deveDevolverValorConvertidoQuandoMoedaDePagamentoEhDiferente() {
            SimulacaoPrecificacaoRQ requisicao = new SimulacaoPrecificacaoRQ(
                    new BigDecimal("10000.00"), LocalDate.now().plusDays(87), DUPLICATA, "BRL", "USD", TAXA_BASE);
            when(cambioService.converter(any(), eq("BRL"), eq("USD"))).thenReturn(new BigDecimal("1833.6000"));

            PrecificacaoRS resposta = precificacaoService.simular(requisicao);

            assertThat(resposta.vlConvertido()).isEqualByComparingTo("1833.6000");
            assertThat(resposta.sgMoedaPagamento()).isEqualTo("USD");
        }

        @Test
        @DisplayName("deve propagar PrecificacaoException quando o vencimento não é futuro")
        void devePropagarExcecaoQuandoVencimentoNaoEhFuturo() {
            SimulacaoPrecificacaoRQ requisicao = new SimulacaoPrecificacaoRQ(
                    new BigDecimal("10000.00"), LocalDate.now().minusDays(1), DUPLICATA, "BRL", "BRL", TAXA_BASE);

            assertThatThrownBy(() -> precificacaoService.simular(requisicao))
                    .isInstanceOf(PrecificacaoException.class)
                    .hasMessageContaining("Prazo inválido");
        }
    }

    private static Recebivel recebivel(String tipo, BigDecimal vlFace, long prazoDias) {
        Recebivel recebivel = new Recebivel();
        recebivel.setVlFace(vlFace);
        recebivel.setDtCriacao(LocalDateTime.now());
        recebivel.setDtVencimento(LocalDate.now().plusDays(prazoDias));
        recebivel.setRecebivelTipo(new RecebivelTipo(null, tipo));
        return recebivel;
    }

    private static Recebivel recebivelComMoeda(String tipo, BigDecimal vlFace, long prazoDias, String sgMoeda) {
        Recebivel recebivel = recebivel(tipo, vlFace, prazoDias);
        recebivel.setMoeda(moeda(1L, sgMoeda));
        return recebivel;
    }

    private static Moeda moeda(Long id, String sigla) {
        Moeda moeda = new Moeda();
        moeda.setId(id);
        moeda.setSgMoeda(sigla);
        return moeda;
    }
}
