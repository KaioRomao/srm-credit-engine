package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExtratoItemRS(
        Long liquidacaoId,
        String trackId,
        LocalDateTime dtLiquidacao,
        Long cedenteId,
        String cedenteNome,
        String cedenteDocumento,
        String nrTitulo,
        String tipoRecebivel,
        LocalDate dtVencimento,
        BigDecimal vlFace,
        String sgMoedaOrigem,
        BigDecimal vlLiquido,
        BigDecimal vlSpread,
        BigDecimal vlTaxaBase,
        Integer qtPrazoDia,
        BigDecimal vlLiquidado,
        BigDecimal vlCambioAplicado,
        String sgMoedaLiquidacao) {}
