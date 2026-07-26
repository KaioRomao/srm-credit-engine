package br.com.srm.credit.engine.dto.rq;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SimulacaoPrecificacaoRQ(
        @NotNull(message = "vlFace obrigatório") @Positive(message = "vlFace deve ser positivo") BigDecimal vlFace,
        @NotNull(message = "dtVencimento obrigatória") LocalDate dtVencimento,
        @NotBlank(message = "tipoRecebivel obrigatório") String tipoRecebivel,
        @NotBlank(message = "sgMoeda obrigatória") String sgMoeda,
        String sgMoedaPagamento,
        @NotNull(message = "vlTaxaBase obrigatória") BigDecimal vlTaxaBase) {}
