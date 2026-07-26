package br.com.srm.credit.engine.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.Moeda;

public interface MoedaRepository extends JpaRepository<Moeda, Long> {

    Optional<Moeda> findBySgMoeda(String sgMoeda);
}
