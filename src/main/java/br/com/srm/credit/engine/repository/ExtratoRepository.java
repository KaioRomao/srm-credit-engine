package br.com.srm.credit.engine.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import br.com.srm.credit.engine.dto.rq.ExtratoFiltroRQ;
import br.com.srm.credit.engine.dto.rs.ExtratoItemRS;
import br.com.srm.credit.engine.exception.FiltroInvalidoException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ExtratoRepository {

    private static final int TAMANHO_MAX_PAGINA = 100;

    private static final Map<String, String> COLUNAS_ORDENACAO = Map.of(
            "dtLiquidacao", "l.dt_liquidacao",
            "vlLiquidado", "l.vl_liquidado",
            "vlFace", "r.vl_face",
            "dtVencimento", "r.dt_vencimento",
            "cedenteNome", "c.nm_cedente",
            "sgMoedaLiquidacao", "ml.sg_moeda");

    private static final String SELECT_ITENS =
            """
            SELECT l.id                 AS liquidacao_id,
                   l.track_id           AS track_id,
                   l.dt_liquidacao      AS dt_liquidacao,
                   c.id                 AS cedente_id,
                   c.nm_cedente         AS cedente_nome,
                   c.nr_documento       AS cedente_documento,
                   r.nr_titulo          AS nr_titulo,
                   t.ds_recebivel_tipo  AS tipo_recebivel,
                   r.dt_vencimento      AS dt_vencimento,
                   r.vl_face            AS vl_face,
                   mo.sg_moeda          AS sg_moeda_origem,
                   p.vl_liquido         AS vl_liquido,
                   p.vl_spread          AS vl_spread,
                   p.vl_taxa_base       AS vl_taxa_base,
                   p.qt_prazo_dia       AS qt_prazo_dia,
                   l.vl_liquidado       AS vl_liquidado,
                   l.vl_cambio_aplicado AS vl_cambio_aplicado,
                   ml.sg_moeda          AS sg_moeda_liquidacao
            """;

    private static final String FROM_WHERE =
            """
            FROM liquidacao l
            JOIN cedente c        ON c.id = l.cedente_id
            JOIN moeda ml         ON ml.id = l.moeda_liquidacao_id
            JOIN precificacao p   ON p.id = l.precificacao_id
            JOIN recebivel r      ON r.id = p.recebivel_id
            JOIN recebivel_tipo t ON t.id = r.recebivel_tipo_id
            JOIN moeda mo         ON mo.id = r.moeda_id
            WHERE l.st_liquidacao = 'LIQUIDADA'
            """;

    private final EntityManager entityManager;

    public Page<ExtratoItemRS> buscar(ExtratoFiltroRQ filtro, Pageable pageable) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        String filtros = montarFiltros(filtro, parametros);

        Pageable paginacao = limitarTamanho(pageable);

        long total = contar(filtros, parametros);
        if (total == 0) {
            return new PageImpl<>(List.of(), paginacao, 0);
        }

        Query query = entityManager.createNativeQuery(
                SELECT_ITENS + FROM_WHERE + filtros + " ORDER BY " + montarOrdenacao(paginacao.getSort()), Tuple.class);
        parametros.forEach(query::setParameter);
        query.setFirstResult((int) paginacao.getOffset());
        query.setMaxResults(paginacao.getPageSize());

        @SuppressWarnings("unchecked")
        List<Tuple> linhas = query.getResultList();
        List<ExtratoItemRS> itens =
                linhas.stream().map(ExtratoRepository::mapear).toList();

        return new PageImpl<>(itens, paginacao, total);
    }

    private long contar(String filtros, Map<String, Object> parametros) {
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) " + FROM_WHERE + filtros);
        parametros.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    private String montarFiltros(ExtratoFiltroRQ filtro, Map<String, Object> parametros) {
        List<String> condicoes = new ArrayList<>();

        if (filtro.dataInicio() != null) {
            condicoes.add("AND l.dt_liquidacao >= :dataInicio");
            parametros.put("dataInicio", filtro.dataInicio().atStartOfDay());
        }
        if (filtro.dataFim() != null) {
            condicoes.add("AND l.dt_liquidacao < :dataFimExclusivo");
            parametros.put("dataFimExclusivo", filtro.dataFim().plusDays(1).atStartOfDay());
        }
        if (filtro.cedenteId() != null) {
            condicoes.add("AND l.cedente_id = :cedenteId");
            parametros.put("cedenteId", filtro.cedenteId());
        }
        if (filtro.sgMoeda() != null && !filtro.sgMoeda().isBlank()) {
            condicoes.add("AND ml.sg_moeda = :sgMoeda");
            parametros.put("sgMoeda", filtro.sgMoeda().trim().toUpperCase());
        }

        return condicoes.isEmpty() ? "" : " " + String.join(" ", condicoes) + " ";
    }

    private String montarOrdenacao(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "l.dt_liquidacao DESC";
        }
        return sort.stream()
                .map(ordem -> {
                    String coluna = COLUNAS_ORDENACAO.get(ordem.getProperty());
                    if (coluna == null) {
                        throw new FiltroInvalidoException("Ordenação não suportada: '" + ordem.getProperty()
                                + "'. Campos permitidos: "
                                + COLUNAS_ORDENACAO.keySet().stream().sorted().toList());
                    }
                    return coluna + (ordem.isAscending() ? " ASC" : " DESC");
                })
                .collect(Collectors.joining(", "));
    }

    private Pageable limitarTamanho(Pageable pageable) {
        if (pageable.getPageSize() <= TAMANHO_MAX_PAGINA) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), TAMANHO_MAX_PAGINA, pageable.getSort());
    }

    private static ExtratoItemRS mapear(Tuple linha) {
        return new ExtratoItemRS(
                (Long) linha.get("liquidacao_id"),
                (String) linha.get("track_id"),
                (LocalDateTime) linha.get("dt_liquidacao"),
                (Long) linha.get("cedente_id"),
                (String) linha.get("cedente_nome"),
                (String) linha.get("cedente_documento"),
                (String) linha.get("nr_titulo"),
                (String) linha.get("tipo_recebivel"),
                (LocalDate) linha.get("dt_vencimento"),
                (BigDecimal) linha.get("vl_face"),
                (String) linha.get("sg_moeda_origem"),
                (BigDecimal) linha.get("vl_liquido"),
                (BigDecimal) linha.get("vl_spread"),
                (BigDecimal) linha.get("vl_taxa_base"),
                (Integer) linha.get("qt_prazo_dia"),
                (BigDecimal) linha.get("vl_liquidado"),
                (BigDecimal) linha.get("vl_cambio_aplicado"),
                (String) linha.get("sg_moeda_liquidacao"));
    }
}
