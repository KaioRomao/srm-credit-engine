package br.com.srm.credit.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import br.com.srm.credit.engine.dto.rq.ExtratoFiltroRQ;
import br.com.srm.credit.engine.dto.rs.ExtratoItemRS;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.exception.FiltroInvalidoException;
import br.com.srm.credit.engine.support.DadosDeTeste;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integracao")
@Import(ExtratoRepository.class)
@DisplayName("ExtratoRepository — query nativa do extrato")
class ExtratoRepositoryIT {

    private static final ExtratoFiltroRQ SEM_FILTRO = new ExtratoFiltroRQ(null, null, null, null);

    @Autowired
    private ExtratoRepository extratoRepository;

    @Autowired
    private EntityManager entityManager;

    private Cedente acme;
    private Cedente beta;
    private Moeda brl;
    private Moeda usd;

    @BeforeEach
    void popularBase() {
        entityManager.createQuery("DELETE FROM Liquidacao").executeUpdate();
        entityManager.createQuery("DELETE FROM Precificacao").executeUpdate();
        entityManager.createQuery("DELETE FROM Recebivel").executeUpdate();
        entityManager.createQuery("DELETE FROM Lote").executeUpdate();
        entityManager.createQuery("DELETE FROM Cedente").executeUpdate();

        brl = entityManager
                .createQuery("SELECT m FROM Moeda m WHERE m.sgMoeda = 'BRL'", Moeda.class)
                .getSingleResult();
        usd = entityManager
                .createQuery("SELECT m FROM Moeda m WHERE m.sgMoeda = 'USD'", Moeda.class)
                .getSingleResult();
        RecebivelTipo duplicata = entityManager
                .createQuery(
                        "SELECT t FROM RecebivelTipo t WHERE t.dsRecebivelTipo = 'DUPLICATA_MERCANTIL'",
                        RecebivelTipo.class)
                .getSingleResult();

        acme = persistir(DadosDeTeste.cedente("ACME LTDA", DadosDeTeste.CNPJ_ACME));
        beta = persistir(DadosDeTeste.cedente("BETA EXPORTADORA", DadosDeTeste.CNPJ_BETA));
        Lote lote = persistir(DadosDeTeste.lote("Lote extrato"));

        criarLiquidacao(
                "A-BRL",
                new BigDecimal("10000.0000"),
                acme,
                lote,
                duplicata,
                brl,
                StatusLiquidacao.LIQUIDADA,
                LocalDateTime.of(2026, 7, 10, 10, 0));
        criarLiquidacao(
                "A-USD",
                new BigDecimal("20000.0000"),
                acme,
                lote,
                duplicata,
                usd,
                StatusLiquidacao.LIQUIDADA,
                LocalDateTime.of(2026, 7, 20, 23, 30));
        criarLiquidacao(
                "B-USD",
                new BigDecimal("30000.0000"),
                beta,
                lote,
                duplicata,
                usd,
                StatusLiquidacao.LIQUIDADA,
                LocalDateTime.of(2026, 7, 31, 23, 59));
        criarLiquidacao(
                "B-FALHA", new BigDecimal("40000.0000"), beta, lote, duplicata, brl, StatusLiquidacao.FALHA, null);
        criarLiquidacao(
                "B-PENDENTE",
                new BigDecimal("50000.0000"),
                beta,
                lote,
                duplicata,
                brl,
                StatusLiquidacao.PENDENTE,
                null);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("deve retornar somente liquidações LIQUIDADA quando não há filtros")
    void deveRetornarSomenteLiquidacoesLiquidadasQuandoNaoHaFiltros() {
        Page<ExtratoItemRS> pagina = extratoRepository.buscar(SEM_FILTRO, PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements())
                .as("FALHA e PENDENTE não entram no extrato")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("deve ordenar por data de liquidação decrescente quando não há sort explícito")
    void deveOrdenarPorDataDeLiquidacaoDecrescenteQuandoNaoHaSortExplicito() {
        Page<ExtratoItemRS> pagina = extratoRepository.buscar(SEM_FILTRO, PageRequest.of(0, 20, Sort.unsorted()));

        assertThat(pagina.getContent()).extracting(ExtratoItemRS::nrTitulo).containsExactly("B-USD", "A-USD", "A-BRL");
    }

    @Test
    @DisplayName("deve filtrar por cedente quando cedenteId é informado")
    void deveFiltrarPorCedenteQuandoCedenteIdEhInformado() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(null, null, acme.getId(), null);

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(2).allSatisfy(item -> assertThat(item.cedenteNome())
                .isEqualTo("ACME LTDA"));
    }

    @Test
    @DisplayName("deve filtrar por moeda quando sgMoeda é informada")
    void deveFiltrarPorMoedaQuandoSgMoedaEhInformada() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(null, null, null, "USD");

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(2).allSatisfy(item -> assertThat(item.sgMoedaLiquidacao())
                .isEqualTo("USD"));
    }

    @Test
    @DisplayName("deve normalizar a moeda para maiúscula quando informada em minúscula")
    void deveNormalizarMoedaParaMaiusculaQuandoInformadaEmMinuscula() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(null, null, null, " usd ");

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("deve combinar filtros quando cedente e moeda são informados juntos")
    void deveCombinarFiltrosQuandoCedenteEMoedaSaoInformadosJuntos() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(null, null, beta.getId(), "USD");

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1).first().satisfies(item -> assertThat(item.nrTitulo())
                .isEqualTo("B-USD"));
    }

    @Test
    @DisplayName("deve incluir o último dia inteiro do período quando a liquidação foi após a meia-noite")
    void deveIncluirUltimoDiaInteiroDoPeriodoQuandoLiquidacaoFoiAposMeiaNoite() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), null, null);

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .as("A-USD liquidou às 23:30 do dia 20 — um <= dataFim o descartaria")
                .hasSize(1)
                .first()
                .satisfies(item -> assertThat(item.nrTitulo()).isEqualTo("A-USD"));
    }

    @Test
    @DisplayName("deve retornar página vazia quando o período não contém liquidações")
    void deveRetornarPaginaVaziaQuandoPeriodoNaoContemLiquidacoes() {
        ExtratoFiltroRQ filtro = new ExtratoFiltroRQ(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null);

        Page<ExtratoItemRS> pagina = extratoRepository.buscar(filtro, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("deve paginar corretamente quando o tamanho de página é menor que o total")
    void devePaginarCorretamenteQuandoTamanhoDePaginaEhMenorQueOTotal() {
        Page<ExtratoItemRS> primeira = extratoRepository.buscar(SEM_FILTRO, PageRequest.of(0, 2));
        Page<ExtratoItemRS> segunda = extratoRepository.buscar(SEM_FILTRO, PageRequest.of(1, 2));

        assertThat(primeira.getContent()).hasSize(2);
        assertThat(primeira.isFirst()).isTrue();
        assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(segunda.getContent()).hasSize(1);
        assertThat(segunda.isLast()).isTrue();
    }

    @Test
    @DisplayName("deve limitar o tamanho da página a 100 quando o cliente pede mais")
    void deveLimitarTamanhoDaPaginaA100QuandoClientePedeMais() {
        Page<ExtratoItemRS> pagina = extratoRepository.buscar(SEM_FILTRO, PageRequest.of(0, 5000));

        assertThat(pagina.getSize())
                .as("teto impede varredura da tabela inteira")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("deve ordenar por valor de face crescente quando o sort é vlFace asc")
    void deveOrdenarPorValorDeFaceCrescenteQuandoSortEhVlFaceAsc() {
        Page<ExtratoItemRS> pagina =
                extratoRepository.buscar(SEM_FILTRO, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "vlFace")));

        assertThat(pagina.getContent()).extracting(ExtratoItemRS::vlFace).isSortedAccordingTo(BigDecimal::compareTo);
    }

    @Test
    @DisplayName("deve aceitar múltiplos campos de ordenação quando ambos estão na whitelist")
    void deveAceitarMultiplosCamposDeOrdenacaoQuandoAmbosEstaoNaWhitelist() {
        Page<ExtratoItemRS> pagina = extratoRepository.buscar(
                SEM_FILTRO,
                PageRequest.of(0, 20, Sort.by(Sort.Order.asc("cedenteNome"), Sort.Order.desc("vlLiquidado"))));

        assertThat(pagina.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("deve lançar FiltroInvalidoException quando o campo de ordenação não está na whitelist")
    void deveLancarFiltroInvalidoQuandoCampoDeOrdenacaoNaoEstaNaWhitelist() {
        PageRequest paginacao = PageRequest.of(0, 20, Sort.by("dsObservacao"));

        assertThatThrownBy(() -> extratoRepository.buscar(SEM_FILTRO, paginacao))
                .isInstanceOf(FiltroInvalidoException.class)
                .hasMessageContaining("dsObservacao");
    }

    @Test
    @DisplayName("deve rejeitar tentativa de injeção de SQL pelo parâmetro de ordenação")
    void deveRejeitarTentativaDeInjecaoDeSqlPeloParametroDeOrdenacao() {
        PageRequest paginacao = PageRequest.of(0, 20, Sort.by("vl_liquidado; DROP TABLE liquidacao"));

        assertThatThrownBy(() -> extratoRepository.buscar(SEM_FILTRO, paginacao))
                .isInstanceOf(FiltroInvalidoException.class);
    }

    @Test
    @DisplayName("deve projetar todos os campos do snapshot quando devolve um item")
    void deveProjetarTodosOsCamposDoSnapshotQuandoDevolveUmItem() {
        Page<ExtratoItemRS> pagina =
                extratoRepository.buscar(new ExtratoFiltroRQ(null, null, acme.getId(), "BRL"), PageRequest.of(0, 1));

        assertThat(pagina.getContent()).first().satisfies(item -> {
            assertThat(item.liquidacaoId()).isNotNull();
            assertThat(item.trackId()).isNotBlank();
            assertThat(item.dtLiquidacao()).isNotNull();
            assertThat(item.cedenteDocumento()).isEqualTo(DadosDeTeste.CNPJ_ACME);
            assertThat(item.tipoRecebivel()).isEqualTo("DUPLICATA_MERCANTIL");
            assertThat(item.dtVencimento()).isNotNull();
            assertThat(item.vlSpread()).isEqualByComparingTo("0.015000");
            assertThat(item.vlTaxaBase()).isEqualByComparingTo("0.010000");
            assertThat(item.qtPrazoDia()).isEqualTo(87);
            assertThat(item.sgMoedaOrigem()).isEqualTo("BRL");
        });
    }

    private void criarLiquidacao(
            String nrTitulo,
            BigDecimal vlFace,
            Cedente cedente,
            Lote lote,
            RecebivelTipo tipo,
            Moeda moedaLiquidacao,
            StatusLiquidacao status,
            LocalDateTime dtLiquidacao) {
        Recebivel recebivel = persistir(DadosDeTeste.recebivel(nrTitulo, vlFace, cedente, lote, brl, tipo));
        Precificacao precificacao = persistir(DadosDeTeste.precificacao(
                recebivel, vlFace.multiply(new BigDecimal("0.93")), new BigDecimal("0.015000")));
        persistir(DadosDeTeste.liquidacao(precificacao, cedente, moedaLiquidacao, status, dtLiquidacao));
    }

    private <T> T persistir(T entidade) {
        entityManager.persist(entidade);
        return entidade;
    }
}
