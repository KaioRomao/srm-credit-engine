package br.com.srm.credit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.repository.CedenteRepository;
import br.com.srm.credit.engine.repository.LiquidacaoRepository;
import br.com.srm.credit.engine.repository.LoteRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.repository.RecebivelRepository;
import br.com.srm.credit.engine.repository.RecebivelTipoRepository;
import br.com.srm.credit.engine.service.CambioService;
import br.com.srm.credit.engine.service.LiquidacaoService;
import br.com.srm.credit.engine.support.DadosDeTeste;

@SpringBootTest
@ActiveProfiles("integracao")
@DisplayName("Retry automatico em conflito de versao")
class RetryOptimisticLockIT {

    @MockitoSpyBean
    private CambioService cambioService;

    @Autowired
    private LiquidacaoService liquidacaoService;

    @Autowired
    private LiquidacaoRepository liquidacaoRepository;

    @Autowired
    private PrecificacaoRepository precificacaoRepository;

    @Autowired
    private RecebivelRepository recebivelRepository;

    @Autowired
    private RecebivelTipoRepository recebivelTipoRepository;

    @Autowired
    private CedenteRepository cedenteRepository;

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private MoedaRepository moedaRepository;

    @Test
    @DisplayName("deve concluir a liquidacao quando o primeiro commit falha por conflito de versao")
    void deveConcluirLiquidacaoQuandoPrimeiroCommitFalhaPorConflitoDeVersao() {
        Long liquidacaoId = criarLiquidacaoEmProcessamento();
        AtomicInteger tentativas = new AtomicInteger();

        doAnswer(invocacao -> {
                    if (tentativas.incrementAndGet() == 1) {
                        throw new OptimisticLockingFailureException("conflito simulado na primeira tentativa");
                    }
                    return invocacao.callRealMethod();
                })
                .when(cambioService)
                .converter(any(), any(), any());

        liquidacaoService.finalizaLiquidacao(liquidacaoId);

        assertThat(tentativas.get())
                .as("o retry deve reexecutar o metodo, nao apenas propagar a excecao")
                .isEqualTo(2);

        Liquidacao persistida = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        assertThat(persistida.getStatus())
                .as("a segunda tentativa precisa concluir a liquidacao")
                .isEqualTo(StatusLiquidacao.LIQUIDADA);
        assertThat(persistida.getVlLiquidado()).isNotNull();
    }

    @Test
    @DisplayName("deve reexecutar em transacao nova a cada tentativa quando ha conflito")
    void deveReexecutarEmTransacaoNovaACadaTentativaQuandoHaConflito() {
        Long liquidacaoId = criarLiquidacaoEmProcessamento();
        AtomicInteger tentativas = new AtomicInteger();

        doAnswer(invocacao -> {
                    if (tentativas.incrementAndGet() <= 2) {
                        throw new OptimisticLockingFailureException("conflito nas duas primeiras tentativas");
                    }
                    return invocacao.callRealMethod();
                })
                .when(cambioService)
                .converter(any(), any(), any());

        liquidacaoService.finalizaLiquidacao(liquidacaoId);

        assertThat(tentativas.get()).isEqualTo(3);
        Liquidacao persistida = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        assertThat(persistida.getStatus())
                .as("transacao anterior sofreu rollback, entao a versao relida esta consistente")
                .isEqualTo(StatusLiquidacao.LIQUIDADA);
        assertThat(persistida.getVersion())
                .as("apenas o commit bem-sucedido incrementa a versao")
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("deve propagar a excecao quando o conflito persiste alem do limite de tentativas")
    void devePropagarExcecaoQuandoConflitoPersisteAlemDoLimite() {
        Long liquidacaoId = criarLiquidacaoEmProcessamento();
        AtomicInteger tentativas = new AtomicInteger();

        doAnswer(invocacao -> {
                    tentativas.incrementAndGet();
                    throw new OptimisticLockingFailureException("conflito permanente");
                })
                .when(cambioService)
                .converter(any(), any(), any());

        assertThatThrownBy(() -> liquidacaoService.finalizaLiquidacao(liquidacaoId))
                .as("contencao persistente e problema real, precisa chegar na DLQ")
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(tentativas.get())
                .as("1 execucao inicial mais 3 retentativas")
                .isEqualTo(4);

        Liquidacao persistida = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        assertThat(persistida.getStatus()).as("nenhuma tentativa commitou").isEqualTo(StatusLiquidacao.PROCESSANDO);
    }

    @Test
    @DisplayName("nao deve retentar quando a falha e de negocio")
    void naoDeveRetentarQuandoFalhaEhDeNegocio() {
        Long liquidacaoId = criarLiquidacaoEmProcessamento();
        AtomicInteger tentativas = new AtomicInteger();

        doAnswer(invocacao -> {
                    tentativas.incrementAndGet();
                    throw new CambioException("Cotação não encontrada para BRL->USD");
                })
                .when(cambioService)
                .converter(any(), any(), any());

        assertThatThrownBy(() -> liquidacaoService.finalizaLiquidacao(liquidacaoId))
                .isInstanceOf(CambioException.class);

        assertThat(tentativas.get())
                .as("erro deterministico nao deve consumir retentativas")
                .isEqualTo(1);
    }

    private Long criarLiquidacaoEmProcessamento() {
        Moeda brl = moedaRepository.findBySgMoeda("BRL").orElseThrow();
        RecebivelTipo duplicata = recebivelTipoRepository
                .findByDsRecebivelTipo("DUPLICATA_MERCANTIL")
                .orElseThrow();
        Cedente cedente = cedenteRepository
                .findByNrDocumento(DadosDeTeste.CNPJ_ACME)
                .orElseGet(() -> cedenteRepository.save(DadosDeTeste.cedente("ACME LTDA", DadosDeTeste.CNPJ_ACME)));
        Lote lote = loteRepository.save(DadosDeTeste.lote("Lote retry"));
        Recebivel recebivel = recebivelRepository.save(DadosDeTeste.recebivel(
                "RETRY-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("10000.0000"),
                cedente,
                lote,
                brl,
                duplicata));
        Precificacao precificacao = precificacaoRepository.save(
                DadosDeTeste.precificacao(recebivel, new BigDecimal("9308.9520"), new BigDecimal("0.015000")));

        Liquidacao liquidacao = DadosDeTeste.liquidacao(precificacao, cedente, brl, StatusLiquidacao.PENDENTE, null);
        Long id = liquidacaoRepository.saveAndFlush(liquidacao).getId();

        liquidacaoService.processaLiquidacao(id);
        return id;
    }
}
