package br.com.srm.credit.engine.dto.rq;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExtratoFiltroRQ(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                @Schema(
                        description = "Início do período de liquidação (inclusivo), formato ISO",
                        example = "2026-07-01")
                LocalDate dataInicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                @Schema(
                        description = "Fim do período de liquidação (inclusivo — o dia inteiro conta), formato ISO",
                        example = "2026-07-31")
                LocalDate dataFim,
        @Schema(description = "Id do cedente", example = "1") Long cedenteId,
        @Schema(description = "Sigla da moeda de liquidação", example = "USD") String sgMoeda) {

    @AssertTrue(message = "dataInicio deve ser anterior ou igual a dataFim")
    @Schema(hidden = true)
    public boolean isPeriodoCoerente() {
        return dataInicio == null || dataFim == null || !dataInicio.isAfter(dataFim);
    }
}
