package br.com.srm.credit.engine.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.RecebivelTipo;

public interface RecebivelTipoRepository extends JpaRepository<RecebivelTipo, Long> {

    Optional<RecebivelTipo> findByDsRecebivelTipo(String dsRecebivelTipo);
}
