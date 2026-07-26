package br.com.srm.credit.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;
import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.ConflitoNegocioException;
import br.com.srm.credit.engine.exception.LiquidacaoException;
import br.com.srm.credit.engine.exception.RecursoNaoEncontradoException;
import br.com.srm.credit.engine.mapper.LiquidacaoMapper;
import br.com.srm.credit.engine.repository.LiquidacaoRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.service.CambioService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LiquidacaoServiceImpl")
class LiquidacaoServiceImplTest {

    private static final UUID TRACK_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Long PRECIFICACAO_ID = 10L;
    private static final Long LIQUIDACAO_ID = 1L;

    @Mock
    private LiquidacaoRepository liquidacaoRepository;

    @Mock
    private PrecificacaoRepository precificacaoRepository;

    @Mock
    private MoedaRepository moedaRepository;

    @Mock
    private CambioService cambioService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private LiquidacaoServiceImpl liquidacaoService;

    @BeforeEach
    void setUp() {
        liquidacaoService = new LiquidacaoServiceImpl(
                liquidacaoRepository,
                precificacaoRepository,
                moedaRepository,
                cambioService,
                new LiquidacaoMapper(),
                applicationEventPublisher);
    }

    @Nested
    @DisplayName("iniciaLiquidacao")
    class IniciaLiquidacao {

        @Test
        @DisplayName("deve criar liquidação PENDENTE e publicar evento quando a requisição é válida")
        void deveCriarLiquidacaoPendenteEPublicarEventoQuandoRequisicaoEhValida() {
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.empty());
            when(precificacaoRepository.findById(PRECIFICACAO_ID)).thenReturn(Optional.of(precificacao()));
            when(liquidacaoRepository.existsByPrecificacaoId(PRECIFICACAO_ID)).thenReturn(false);
            when(moedaRepository.findBySgMoeda("USD")).thenReturn(Optional.of(moeda("USD")));
            when(liquidacaoRepository.save(any(Liquidacao.class))).thenAnswer(invocation -> {
                Liquidacao salva = invocation.getArgument(0);
                salva.setId(LIQUIDACAO_ID);
                return salva;
            });

            LiquidacaoRS resposta = liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("USD"));

            assertThat(resposta.status()).isEqualTo("PENDENTE");
            assertThat(resposta.trackId()).isEqualTo(TRACK_ID.toString());
            assertThat(resposta.vlLiquidado()).isNull();
            verify(applicationEventPublisher).publishEvent(new LiquidacaoMensagem(LIQUIDACAO_ID, TRACK_ID));
        }

        @Test
        @DisplayName("deve devolver a liquidação existente sem duplicar quando o trackId repete")
        void deveDevolverLiquidacaoExistenteSemDuplicarQuandoTrackIdRepete() {
            Liquidacao existente = liquidacao(StatusLiquidacao.LIQUIDADA);
            existente.setVlLiquidado(new BigDecimal("1234.5678"));
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.of(existente));

            LiquidacaoRS resposta = liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("USD"));

            assertThat(resposta.status()).isEqualTo("LIQUIDADA");
            assertThat(resposta.vlLiquidado()).isEqualByComparingTo("1234.5678");
            verify(liquidacaoRepository, never()).save(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        @DisplayName("deve lançar RecursoNaoEncontradoException quando a precificação não existe")
        void deveLancarRecursoNaoEncontradoQuandoPrecificacaoNaoExiste() {
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.empty());
            when(precificacaoRepository.findById(PRECIFICACAO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("USD")))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Precificação não encontrada");
        }

        @Test
        @DisplayName("deve lançar ConflitoNegocioException quando a precificação já foi liquidada")
        void deveLancarConflitoQuandoPrecificacaoJaFoiLiquidada() {
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.empty());
            when(precificacaoRepository.findById(PRECIFICACAO_ID)).thenReturn(Optional.of(precificacao()));
            when(liquidacaoRepository.existsByPrecificacaoId(PRECIFICACAO_ID)).thenReturn(true);

            assertThatThrownBy(() -> liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("USD")))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("já foi liquidada");

            verify(liquidacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar LiquidacaoException quando a precificação não tem valor líquido")
        void deveLancarLiquidacaoExceptionQuandoPrecificacaoNaoTemValorLiquido() {
            Precificacao semValor = precificacao();
            semValor.setVlLiquido(null);
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.empty());
            when(precificacaoRepository.findById(PRECIFICACAO_ID)).thenReturn(Optional.of(semValor));
            when(liquidacaoRepository.existsByPrecificacaoId(PRECIFICACAO_ID)).thenReturn(false);

            assertThatThrownBy(() -> liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("USD")))
                    .isInstanceOf(LiquidacaoException.class)
                    .hasMessageContaining("valor líquido");
        }

        @Test
        @DisplayName("deve lançar CambioException quando a moeda de liquidação não existe")
        void deveLancarCambioExceptionQuandoMoedaDeLiquidacaoNaoExiste() {
            when(liquidacaoRepository.findByTrackId(TRACK_ID)).thenReturn(Optional.empty());
            when(precificacaoRepository.findById(PRECIFICACAO_ID)).thenReturn(Optional.of(precificacao()));
            when(liquidacaoRepository.existsByPrecificacaoId(PRECIFICACAO_ID)).thenReturn(false);
            when(moedaRepository.findBySgMoeda("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> liquidacaoService.iniciaLiquidacao(TRACK_ID, requisicao("XYZ")))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("XYZ");
        }
    }

    @Nested
    @DisplayName("processaLiquidacao")
    class ProcessaLiquidacao {

        @Test
        @DisplayName("deve avançar para PROCESSANDO quando o status é PENDENTE")
        void deveAvancarParaProcessandoQuandoStatusEhPendente() {
            Liquidacao pendente = liquidacao(StatusLiquidacao.PENDENTE);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(pendente));

            liquidacaoService.processaLiquidacao(LIQUIDACAO_ID);

            assertThat(pendente.getStatus()).isEqualTo(StatusLiquidacao.PROCESSANDO);
            assertThat(pendente.getDtAtualizacao()).isNotNull();
            verify(liquidacaoRepository).save(pendente);
        }

        @ParameterizedTest(name = "deve ignorar mensagem duplicada quando o status é {0}")
        @EnumSource(
                value = StatusLiquidacao.class,
                names = {"PROCESSANDO", "LIQUIDADA", "FALHA", "CANCELADA"})
        void deveIgnorarMensagemDuplicadaQuandoStatusNaoEhPendente(StatusLiquidacao status) {
            Liquidacao liquidacao = liquidacao(status);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(liquidacao));

            liquidacaoService.processaLiquidacao(LIQUIDACAO_ID);

            assertThat(liquidacao.getStatus())
                    .as("reentrada não deve alterar estado — protege contra conversão em dobro")
                    .isEqualTo(status);
            verify(liquidacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar RecursoNaoEncontradoException quando a liquidação não existe")
        void deveLancarRecursoNaoEncontradoQuandoLiquidacaoNaoExiste() {
            when(liquidacaoRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> liquidacaoService.processaLiquidacao(404L))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Liquidação não encontrada");
        }
    }

    @Nested
    @DisplayName("finalizaLiquidacao")
    class FinalizaLiquidacao {

        @Test
        @DisplayName("deve gravar valor liquidado e taxa quando converte para a mesma moeda")
        void deveGravarValorLiquidadoETaxaQuandoConverteParaMesmaMoeda() {
            Liquidacao processando = liquidacaoComPrecificacao(StatusLiquidacao.PROCESSANDO, "BRL", "BRL");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));
            when(cambioService.converter(any(), any(), any())).thenReturn(new BigDecimal("9308.9520"));

            liquidacaoService.finalizaLiquidacao(LIQUIDACAO_ID);

            assertThat(processando.getStatus()).isEqualTo(StatusLiquidacao.LIQUIDADA);
            assertThat(processando.getVlLiquidado()).isEqualByComparingTo("9308.9520");
            assertThat(processando.getVlCambioAplicado())
                    .as("mesma moeda deve aplicar taxa 1")
                    .isEqualByComparingTo("1");
            assertThat(processando.getDtLiquidacao()).isNotNull();
        }

        @Test
        @DisplayName("deve calcular taxa aplicada quando converte entre moedas diferentes")
        void deveCalcularTaxaAplicadaQuandoConverteEntreMoedasDiferentes() {
            Liquidacao processando = liquidacaoComPrecificacao(StatusLiquidacao.PROCESSANDO, "BRL", "USD");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));
            when(cambioService.converter(any(), any(), any())).thenReturn(new BigDecimal("1833.6000"));

            liquidacaoService.finalizaLiquidacao(LIQUIDACAO_ID);

            assertThat(processando.getVlLiquidado()).isEqualByComparingTo("1833.6000");
            assertThat(processando.getVlCambioAplicado())
                    .as("1833.6 / 9308.952 = 0.19697168..., HALF_EVEN em 6 casas")
                    .isEqualByComparingTo("0.196972");
        }

        @ParameterizedTest(name = "deve ignorar quando o status é terminal {0}")
        @EnumSource(
                value = StatusLiquidacao.class,
                names = {"LIQUIDADA", "FALHA", "CANCELADA"})
        void deveIgnorarQuandoStatusEhTerminal(StatusLiquidacao terminal) {
            Liquidacao liquidacao = liquidacaoComPrecificacao(terminal, "BRL", "BRL");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(liquidacao));

            liquidacaoService.finalizaLiquidacao(LIQUIDACAO_ID);

            verifyNoInteractions(cambioService);
            verify(liquidacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar ConflitoNegocioException quando tenta finalizar direto de PENDENTE")
        void deveLancarConflitoQuandoTentaFinalizarDiretoDePendente() {
            Liquidacao pendente = liquidacaoComPrecificacao(StatusLiquidacao.PENDENTE, "BRL", "BRL");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(pendente));
            when(cambioService.converter(any(), any(), any())).thenReturn(new BigDecimal("9308.9520"));

            assertThatThrownBy(() -> liquidacaoService.finalizaLiquidacao(LIQUIDACAO_ID))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("PENDENTE -> LIQUIDADA");
        }

        @Test
        @DisplayName("deve propagar CambioException quando não há cotação para o par")
        void devePropagarCambioExceptionQuandoNaoHaCotacao() {
            Liquidacao processando = liquidacaoComPrecificacao(StatusLiquidacao.PROCESSANDO, "BRL", "USD");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));
            when(cambioService.converter(any(), any(), any()))
                    .thenThrow(new CambioException("Cotação não encontrada para BRL->USD"));

            assertThatThrownBy(() -> liquidacaoService.finalizaLiquidacao(LIQUIDACAO_ID))
                    .isInstanceOf(CambioException.class);

            assertThat(processando.getStatus())
                    .as("falha na conversão não deve marcar LIQUIDADA")
                    .isEqualTo(StatusLiquidacao.PROCESSANDO);
        }
    }

    @Nested
    @DisplayName("registrarFalha")
    class RegistrarFalha {

        @Test
        @DisplayName("deve expor a mensagem de domínio quando a causa é ErroDeNegocio")
        void deveExporMensagemDeDominioQuandoCausaEhErroDeNegocio() {
            Liquidacao processando = liquidacao(StatusLiquidacao.PROCESSANDO);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));

            liquidacaoService.registrarFalha(
                    LIQUIDACAO_ID, new CambioException("Cotação não encontrada para BRL->USD"));

            assertThat(processando.getStatus()).isEqualTo(StatusLiquidacao.FALHA);
            assertThat(processando.getDsObservacao()).isEqualTo("Cotação não encontrada para BRL->USD");
        }

        @Test
        @DisplayName("deve usar mensagem genérica quando a causa não é ErroDeNegocio")
        void deveUsarMensagemGenericaQuandoCausaNaoEhErroDeNegocio() {
            Liquidacao processando = liquidacao(StatusLiquidacao.PROCESSANDO);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));

            liquidacaoService.registrarFalha(LIQUIDACAO_ID, new NullPointerException("valor is null"));

            assertThat(processando.getStatus()).isEqualTo(StatusLiquidacao.FALHA);
            assertThat(processando.getDsObservacao())
                    .as("detalhe técnico não pode vazar para o cliente")
                    .doesNotContain("valor is null")
                    .contains("Falha inesperada no processamento");
        }

        @Test
        @DisplayName("deve truncar a observação em 500 caracteres quando a mensagem é maior")
        void deveTruncarObservacaoEm500CaracteresQuandoMensagemEhMaior() {
            Liquidacao processando = liquidacao(StatusLiquidacao.PROCESSANDO);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));

            liquidacaoService.registrarFalha(LIQUIDACAO_ID, new CambioException("x".repeat(900)));

            assertThat(processando.getDsObservacao()).hasSize(500);
        }

        @Test
        @DisplayName("deve aceitar mensagem nula sem quebrar quando a exceção não tem texto")
        void deveAceitarMensagemNulaSemQuebrarQuandoExcecaoNaoTemTexto() {
            Liquidacao processando = liquidacao(StatusLiquidacao.PROCESSANDO);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(processando));

            liquidacaoService.registrarFalha(LIQUIDACAO_ID, new CambioException(null));

            assertThat(processando.getStatus()).isEqualTo(StatusLiquidacao.FALHA);
            assertThat(processando.getDsObservacao()).isNull();
        }

        @ParameterizedTest(name = "não deve lançar nem gravar quando o status já é terminal {0}")
        @EnumSource(
                value = StatusLiquidacao.class,
                names = {"LIQUIDADA", "FALHA", "CANCELADA"})
        void naoDeveLancarNemGravarQuandoStatusJaEhTerminal(StatusLiquidacao terminal) {
            Liquidacao liquidacao = liquidacao(terminal);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(liquidacao));

            liquidacaoService.registrarFalha(LIQUIDACAO_ID, new CambioException("qualquer"));

            assertThat(liquidacao.getStatus())
                    .as("lançar aqui escaparia do listener e geraria requeue infinito")
                    .isEqualTo(terminal);
            verify(liquidacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve marcar FALHA quando o status ainda é PENDENTE")
        void deveMarcarFalhaQuandoStatusAindaEhPendente() {
            Liquidacao pendente = liquidacao(StatusLiquidacao.PENDENTE);
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(pendente));

            liquidacaoService.registrarFalha(LIQUIDACAO_ID, new CambioException("erro antes de processar"));

            assertThat(pendente.getStatus()).isEqualTo(StatusLiquidacao.FALHA);
        }
    }

    @Nested
    @DisplayName("consultaLiquidacao")
    class ConsultaLiquidacao {

        @Test
        @DisplayName("deve devolver o estado atual quando a liquidação existe")
        void deveDevolverEstadoAtualQuandoLiquidacaoExiste() {
            Liquidacao liquidada = liquidacao(StatusLiquidacao.LIQUIDADA);
            liquidada.setVlLiquidado(new BigDecimal("7447.1616"));
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(liquidada));

            LiquidacaoRS resposta = liquidacaoService.consultaLiquidacao(LIQUIDACAO_ID);

            assertThat(resposta.status()).isEqualTo("LIQUIDADA");
            assertThat(resposta.vlLiquidado()).isEqualByComparingTo("7447.1616");
        }

        @Test
        @DisplayName("deve expor o motivo da falha quando o status é FALHA")
        void deveExporMotivoDaFalhaQuandoStatusEhFalha() {
            Liquidacao falha = liquidacao(StatusLiquidacao.FALHA);
            falha.setDsObservacao("Cotação não encontrada para BRL->USD");
            when(liquidacaoRepository.findById(LIQUIDACAO_ID)).thenReturn(Optional.of(falha));

            LiquidacaoRS resposta = liquidacaoService.consultaLiquidacao(LIQUIDACAO_ID);

            assertThat(resposta.dsObservacao()).isEqualTo("Cotação não encontrada para BRL->USD");
        }

        @Test
        @DisplayName("deve lançar RecursoNaoEncontradoException quando o id não existe")
        void deveLancarRecursoNaoEncontradoQuandoIdNaoExiste() {
            when(liquidacaoRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> liquidacaoService.consultaLiquidacao(404L))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }

    private static LiquidacaoRQ requisicao(String sgMoedaLiquidacao) {
        return new LiquidacaoRQ(PRECIFICACAO_ID, sgMoedaLiquidacao);
    }

    private static Moeda moeda(String sigla) {
        Moeda moeda = new Moeda();
        moeda.setSgMoeda(sigla);
        return moeda;
    }

    private static Precificacao precificacao() {
        Cedente cedente = new Cedente();
        cedente.setNmCedente("ACME LTDA");

        Recebivel recebivel = new Recebivel();
        recebivel.setCedente(cedente);
        recebivel.setMoeda(moeda("BRL"));
        recebivel.setDtVencimento(LocalDate.now().plusDays(87));
        recebivel.setDtCriacao(LocalDateTime.now());

        Precificacao precificacao = new Precificacao();
        precificacao.setId(PRECIFICACAO_ID);
        precificacao.setRecebivel(recebivel);
        precificacao.setVlLiquido(new BigDecimal("9308.9520"));
        return precificacao;
    }

    private static Liquidacao liquidacao(StatusLiquidacao status) {
        Liquidacao liquidacao = new Liquidacao();
        liquidacao.setId(LIQUIDACAO_ID);
        liquidacao.setTrackId(TRACK_ID);
        liquidacao.setStatus(status);
        liquidacao.setMoedaLiquidacao(moeda("USD"));
        liquidacao.setDtCriacao(LocalDateTime.now());
        return liquidacao;
    }

    private static Liquidacao liquidacaoComPrecificacao(StatusLiquidacao status, String sgOrigem, String sgDestino) {
        Liquidacao liquidacao = liquidacao(status);
        Precificacao precificacao = precificacao();
        precificacao.getRecebivel().setMoeda(moeda(sgOrigem));
        liquidacao.setPrecificacao(precificacao);
        liquidacao.setMoedaLiquidacao(moeda(sgDestino));
        return liquidacao;
    }
}
