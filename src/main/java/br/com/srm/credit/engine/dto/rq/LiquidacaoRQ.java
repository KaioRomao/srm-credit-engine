package br.com.srm.credit.engine.dto.rq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LiquidacaoRQ(
        @NotNull(message = "precificacaoId obrigatório") Long precificacaoId,
        @NotBlank(message = "sgMoedaLiquidacao obrigatória") String sgMoedaLiquidacao) {}
