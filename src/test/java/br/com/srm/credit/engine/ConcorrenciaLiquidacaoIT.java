package br.com.srm.credit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.repository.CedenteRepository;
import br.com.srm.credit.engine.repository.LiquidacaoRepository;
import br.com.srm.credit.engine.repository.LoteRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.repository.RecebivelRepository;
import br.com.srm.credit.engine.repository.RecebivelTipoRepository;
import br.com.srm.credit.engine.service.LiquidacaoService;
import br.com.srm.credit.engine.support.DadosDeTeste;

@SpringBootTest
@ActiveProfiles("integracao")
@DisplayName("Concorrência na liquidação — @Version e constraints de unicidade")
class ConcorrenciaLiquidacaoIT {

    private static final int THREADS = 6;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private TransactionTemplate transacaoIsolada;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(THREADS);
        transacaoIsolada = new TransactionTemplate(transactionManager);
        transacaoIsolada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("deve lançar OptimisticLockingFailureException quando duas transações atualizam a mesma liquidação")
    void deveLancarOptimisticLockQuandoDuasTransacoesAtualizamAMesmaLiquidacao() throws Exception {
        Long liquidacaoId = criarLiquidacaoPendenteDireto();
        CountDownLatch primeiraCarregou = new CountDownLatch(1);
        CountDownLatch segundaCommitou = new CountDownLatch(1);

        Future<Throwable> transacaoLenta = executor.submit(() -> {
            try {
                transacaoIsolada.execute(status -> {
                    Liquidacao liquidacao =
                            liquidacaoRepository.findById(liquidacaoId).orElseThrow();
                    primeiraCarregou.countDown();
                    aguardar(segundaCommitou);
                    liquidacao.setDsObservacao("atualizacao da transacao lenta");
                    liquidacaoRepository.saveAndFlush(liquidacao);
                    return null;
                });
                return null;
            } catch (Throwable e) {
                return e;
            }
        });

        assertThat(primeiraCarregou.await(10, TimeUnit.SECONDS)).isTrue();

        transacaoIsolada.execute(status -> {
            Liquidacao liquidacao = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
            liquidacao.setDsObservacao("atualizacao da transacao rapida");
            liquidacaoRepository.saveAndFlush(liquidacao);
            return null;
        });
        segundaCommitou.countDown();

        Throwable erro = transacaoLenta.get(15, TimeUnit.SECONDS);

        assertThat(erro)
                .as("a transação que carregou a versão antiga precisa falhar, não sobrescrever")
                .isInstanceOf(OptimisticLockingFailureException.class);

        Liquidacao persistida = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        assertThat(persistida.getDsObservacao())
                .as("o vencedor é quem commitou primeiro")
                .isEqualTo("atualizacao da transacao rapida");
        assertThat(persistida.getVersion())
                .as("uma única atualização foi aplicada, logo a versão avançou uma vez")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("deve criar apenas uma liquidação quando várias threads liquidam a mesma precificação")
    void deveCriarApenasUmaLiquidacaoQuandoVariasThreadsLiquidamAMesmaPrecificacao() throws Exception {
        Long precificacaoId = criarPrecificacaoDisponivel();
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        List<Throwable> falhas = java.util.Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tarefas.add(executor.submit(() -> {
                aguardar(largada);
                try {
                    liquidacaoService.iniciaLiquidacao(UUID.randomUUID(), new LiquidacaoRQ(precificacaoId, "BRL"));
                    sucessos.incrementAndGet();
                } catch (Throwable e) {
                    falhas.add(e);
                }
            }));
        }

        largada.countDown();
        for (Future<?> tarefa : tarefas) {
            tarefa.get(30, TimeUnit.SECONDS);
        }

        assertThat(sucessos.get())
                .as("guard 1:1 em código mais UNIQUE(precificacao_id) no banco")
                .isEqualTo(1);
        assertThat(falhas).hasSize(THREADS - 1);
        assertThat(liquidacoesDaPrecificacao(precificacaoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("deve rejeitar a segunda inserção com violação de unicidade quando o guard em código é contornado")
    void deveRejeitarSegundaInsercaoComViolacaoDeUnicidadeQuandoGuardEmCodigoEhContornado() {
        Long precificacaoId = criarPrecificacaoDisponivel();
        Precificacao precificacao =
                precificacaoRepository.findById(precificacaoId).orElseThrow();
        Moeda brl = moedaRepository.findBySgMoeda("BRL").orElseThrow();
        Cedente cedente = cedenteAcme();

        liquidacaoRepository.saveAndFlush(
                DadosDeTeste.liquidacao(precificacao, cedente, brl, StatusLiquidacao.PENDENTE, null));

        Liquidacao duplicada = DadosDeTeste.liquidacao(precificacao, cedente, brl, StatusLiquidacao.PENDENTE, null);

        assertThatThrownBy(() -> liquidacaoRepository.saveAndFlush(duplicada))
                .as("a constraint do banco é a última linha de defesa, independente do código")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deve rejeitar trackId repetido com violação de unicidade quando duas inserções concorrem")
    void deveRejeitarTrackIdRepetidoComViolacaoDeUnicidadeQuandoDuasInsercoesConcorrem() {
        UUID trackId = UUID.randomUUID();
        Long primeiraPrecificacao = criarPrecificacaoDisponivel();
        Long segundaPrecificacao = criarPrecificacaoDisponivel();

        Liquidacao primeira = liquidacaoComTrackId(primeiraPrecificacao, trackId);
        liquidacaoRepository.saveAndFlush(primeira);

        Liquidacao segunda = liquidacaoComTrackId(segundaPrecificacao, trackId);

        assertThatThrownBy(() -> liquidacaoRepository.saveAndFlush(segunda))
                .as("UNIQUE(track_id) garante idempotência mesmo sob concorrência real")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deve avançar o status uma única vez quando várias threads processam a mesma liquidação")
    void deveAvancarStatusUmaUnicaVezQuandoVariasThreadsProcessamAMesmaLiquidacao() throws Exception {
        Long liquidacaoId = criarLiquidacaoPendenteDireto();
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger avancaram = new AtomicInteger();
        AtomicInteger conflitos = new AtomicInteger();

        List<Future<?>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tarefas.add(executor.submit(() -> {
                aguardar(largada);
                try {
                    liquidacaoService.processaLiquidacao(liquidacaoId);
                    avancaram.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    conflitos.incrementAndGet();
                }
            }));
        }

        largada.countDown();
        for (Future<?> tarefa : tarefas) {
            tarefa.get(30, TimeUnit.SECONDS);
        }

        Liquidacao persistida = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        assertThat(persistida.getStatus()).isEqualTo(StatusLiquidacao.PROCESSANDO);
        assertThat(persistida.getVersion())
                .as("guard de estado mais @Version impedem gravação repetida")
                .isEqualTo(1L);
        assertThat(avancaram.get() + conflitos.get()).isEqualTo(THREADS);
    }

    private Long criarLiquidacaoPendenteDireto() {
        Long precificacaoId = criarPrecificacaoDisponivel();
        Precificacao precificacao =
                precificacaoRepository.findById(precificacaoId).orElseThrow();
        Moeda brl = moedaRepository.findBySgMoeda("BRL").orElseThrow();
        Liquidacao liquidacao =
                DadosDeTeste.liquidacao(precificacao, cedenteAcme(), brl, StatusLiquidacao.PENDENTE, null);
        return liquidacaoRepository.saveAndFlush(liquidacao).getId();
    }

    private Cedente cedenteAcme() {
        return cedenteRepository
                .findByNrDocumento(DadosDeTeste.CNPJ_ACME)
                .orElseGet(() -> cedenteRepository.save(DadosDeTeste.cedente("ACME LTDA", DadosDeTeste.CNPJ_ACME)));
    }

    private Long criarPrecificacaoDisponivel() {
        Moeda brl = moedaRepository.findBySgMoeda("BRL").orElseThrow();
        RecebivelTipo duplicata = recebivelTipoRepository
                .findByDsRecebivelTipo("DUPLICATA_MERCANTIL")
                .orElseThrow();

        Cedente cedente = cedenteAcme();
        Lote lote = loteRepository.save(DadosDeTeste.lote("Lote concorrencia"));
        Recebivel recebivel = recebivelRepository.save(DadosDeTeste.recebivel(
                "CONC-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("10000.0000"),
                cedente,
                lote,
                brl,
                duplicata));

        return precificacaoRepository
                .save(DadosDeTeste.precificacao(recebivel, new BigDecimal("9308.9520"), new BigDecimal("0.015000")))
                .getId();
    }

    private Liquidacao liquidacaoComTrackId(Long precificacaoId, UUID trackId) {
        Precificacao precificacao =
                precificacaoRepository.findById(precificacaoId).orElseThrow();
        Moeda brl = moedaRepository.findBySgMoeda("BRL").orElseThrow();
        Liquidacao liquidacao =
                DadosDeTeste.liquidacao(precificacao, cedenteAcme(), brl, StatusLiquidacao.PENDENTE, null);
        liquidacao.setTrackId(trackId);
        return liquidacao;
    }

    private long liquidacoesDaPrecificacao(Long precificacaoId) {
        return transacaoIsolada.execute(status -> liquidacaoRepository.findAll().stream()
                .filter(l -> l.getPrecificacao().getId().equals(precificacaoId))
                .count());
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout aguardando coordenação entre threads");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
