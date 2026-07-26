package br.com.srm.credit.engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.Lote;

public interface LoteRepository extends JpaRepository<Lote, Long> {}
