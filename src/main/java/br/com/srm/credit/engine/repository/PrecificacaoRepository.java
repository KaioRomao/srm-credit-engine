package br.com.srm.credit.engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.Precificacao;

public interface PrecificacaoRepository extends JpaRepository<Precificacao, Long> {}
