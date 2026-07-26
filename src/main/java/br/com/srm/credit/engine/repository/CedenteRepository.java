package br.com.srm.credit.engine.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.credit.engine.entity.Cedente;

public interface CedenteRepository extends JpaRepository<Cedente, Long> {

    Optional<Cedente> findByNrDocumento(String nrDocumento);
}
