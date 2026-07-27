package br.com.srm.credit.engine.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.enums.StatusLiquidacao;

public interface LiquidacaoRepository extends JpaRepository<Liquidacao, Long> {

    @EntityGraph(attributePaths = "moedaLiquidacao")
    @Query(
            """
            SELECT l FROM Liquidacao l
            WHERE (:id IS NULL OR l.id = :id)
              AND (:trackId IS NULL OR l.trackId = :trackId)
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<Liquidacao> buscarPorFiltro(
            @Param("id") Long id,
            @Param("trackId") UUID trackId,
            @Param("status") StatusLiquidacao status,
            Pageable pageable);

    Optional<Liquidacao> findByTrackId(UUID trackId);

    boolean existsByPrecificacaoId(Long id);
}
