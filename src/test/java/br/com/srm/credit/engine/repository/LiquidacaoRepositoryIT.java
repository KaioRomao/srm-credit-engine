package br.com.srm.credit.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.support.DadosDeTeste;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integracao")
@DisplayName("LiquidacaoRepository — busca paginada com filtros")
class LiquidacaoRepositoryIT {

    @Autowired
    private LiquidacaoRepository liquidacaoRepository;

    @Autowired
    private EntityManager entityManager;

    private Liquidacao liquidada;
    private Liquidacao pendente;
    private Liquidacao falha;

    @BeforeEach
    void popularBase() {
        entityManager.createQuery("DELETE FROM Liquidacao").executeUpdate();
        entityManager.createQuery("DELETE FROM Precificacao").executeUpdate();
        entityManager.createQuery("DELETE FROM Recebivel").executeUpdate();
        entityManager.createQuery("DELETE FROM Lote").executeUpdate();
        entityManager.createQuery("DELETE FROM Cedente").executeUpdate();

        Moeda brl = entityManager
                .createQuery("SELECT m FROM Moeda m WHERE m.sgMoeda = 'BRL'", Moeda.class)
                .getSingleResult();
        Moeda usd = entityManager
                .createQuery("SELECT m FROM Moeda m WHERE m.sgMoeda = 'USD'", Moeda.class)
                .getSingleResult();
        RecebivelTipo duplicata = entityManager
                .createQuery(
                        "SELECT t FROM RecebivelTipo t WHERE t.dsRecebivelTipo = 'DUPLICATA_MERCANTIL'",
                        RecebivelTipo.class)
                .getSingleResult();

        Cedente acme = persistir(DadosDeTeste.cedente("ACME LTDA", DadosDeTeste.CNPJ_ACME));
        Lote lote = persistir(DadosDeTeste.lote("Lote listagem"));

        liquidada = criarLiquidacao(
                "L-1",
                acme,
                lote,
                duplicata,
                brl,
                usd,
                StatusLiquidacao.LIQUIDADA,
                LocalDateTime.of(2026, 7, 10, 10, 0));
        pendente = criarLiquidacao("L-2", acme, lote, duplicata, brl, brl, StatusLiquidacao.PENDENTE, null);
        falha = criarLiquidacao("L-3", acme, lote, duplicata, brl, brl, StatusLiquidacao.FALHA, null);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("deve retornar todas as liquidações quando não há filtros")
    void deveRetornarTodasAsLiquidacoesQuandoNaoHaFiltros() {
        Page<Liquidacao> pagina = liquidacaoRepository.buscarPorFiltro(null, null, null, PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("deve filtrar por id quando informado")
    void deveFiltrarPorIdQuandoInformado() {
        Page<Liquidacao> pagina =
                liquidacaoRepository.buscarPorFiltro(pendente.getId(), null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1).first().satisfies(l -> assertThat(l.getId())
                .isEqualTo(pendente.getId()));
    }

    @Test
    @DisplayName("deve filtrar por trackId quando informado")
    void deveFiltrarPorTrackIdQuandoInformado() {
        Page<Liquidacao> pagina =
                liquidacaoRepository.buscarPorFiltro(null, liquidada.getTrackId(), null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1).first().satisfies(l -> assertThat(l.getTrackId())
                .isEqualTo(liquidada.getTrackId()));
    }

    @Test
    @DisplayName("deve filtrar por status quando informado")
    void deveFiltrarPorStatusQuandoInformado() {
        Page<Liquidacao> pagina =
                liquidacaoRepository.buscarPorFiltro(null, null, StatusLiquidacao.FALHA, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1).first().satisfies(l -> assertThat(l.getId())
                .isEqualTo(falha.getId()));
    }

    @Test
    @DisplayName("deve combinar filtros quando id e status são informados juntos")
    void deveCombinarFiltrosQuandoIdEStatusSaoInformadosJuntos() {
        Page<Liquidacao> comStatusCerto = liquidacaoRepository.buscarPorFiltro(
                liquidada.getId(), null, StatusLiquidacao.LIQUIDADA, PageRequest.of(0, 20));
        Page<Liquidacao> comStatusDivergente = liquidacaoRepository.buscarPorFiltro(
                liquidada.getId(), null, StatusLiquidacao.PENDENTE, PageRequest.of(0, 20));

        assertThat(comStatusCerto.getTotalElements()).isEqualTo(1);
        assertThat(comStatusDivergente.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("deve carregar a moeda de liquidação junto para o mapper não disparar query extra")
    void deveCarregarMoedaDeLiquidacaoJunto() {
        Page<Liquidacao> pagina =
                liquidacaoRepository.buscarPorFiltro(liquidada.getId(), null, null, PageRequest.of(0, 20));

        entityManager.clear();

        assertThat(pagina.getContent())
                .first()
                .satisfies(l -> assertThat(l.getMoedaLiquidacao().getSgMoeda()).isEqualTo("USD"));
    }

    @Test
    @DisplayName("deve paginar e ordenar pelo sort do pageable")
    void devePaginarEOrdenarPeloSortDoPageable() {
        Page<Liquidacao> primeira = liquidacaoRepository.buscarPorFiltro(
                null, null, null, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(primeira.getContent())
                .extracting(Liquidacao::getId)
                .containsExactly(falha.getId(), pendente.getId());
    }

    private Liquidacao criarLiquidacao(
            String nrTitulo,
            Cedente cedente,
            Lote lote,
            RecebivelTipo tipo,
            Moeda moedaOrigem,
            Moeda moedaLiquidacao,
            StatusLiquidacao status,
            LocalDateTime dtLiquidacao) {
        Recebivel recebivel = persistir(
                DadosDeTeste.recebivel(nrTitulo, new BigDecimal("10000.0000"), cedente, lote, moedaOrigem, tipo));
        Precificacao precificacao = persistir(
                DadosDeTeste.precificacao(recebivel, new BigDecimal("9300.0000"), new BigDecimal("0.015000")));
        return persistir(DadosDeTeste.liquidacao(precificacao, cedente, moedaLiquidacao, status, dtLiquidacao));
    }

    private <T> T persistir(T entidade) {
        entityManager.persist(entidade);
        return entidade;
    }
}
