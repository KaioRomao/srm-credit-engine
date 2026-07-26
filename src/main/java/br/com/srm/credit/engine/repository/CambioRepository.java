package br.com.srm.credit.engine.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.srm.credit.engine.entity.Cambio;

public interface CambioRepository extends JpaRepository<Cambio, Long> {

    @Query(
            """
                SELECT c
                FROM Cambio c
                WHERE c.moedaOrigem.sgMoeda = :origem
                  AND c.moedaDestino.sgMoeda = :destino
                ORDER BY c.dtFechamento DESC
                LIMIT 1
            """)
    Optional<Cambio> buscarUltimoCambio(String origem, String destino);

    @Query(
            """
                SELECT c
                FROM Cambio c
                WHERE c.moedaOrigem.sgMoeda = :origem
                  AND c.moedaDestino.sgMoeda = :destino
                  AND c.dtFechamento = :dtFechamento
            """)
    Optional<Cambio> buscarCambioDoFechamento(String origem, String destino, LocalDateTime dtFechamento);
}
