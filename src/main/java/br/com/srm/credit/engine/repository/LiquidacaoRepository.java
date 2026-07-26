package br.com.srm.credit.engine.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.Liquidacao;

public interface LiquidacaoRepository extends JpaRepository<Liquidacao, Long> {

    Optional<Liquidacao> findByTrackId(UUID trackId);

    boolean existsByPrecificacaoId(Long id);
}
