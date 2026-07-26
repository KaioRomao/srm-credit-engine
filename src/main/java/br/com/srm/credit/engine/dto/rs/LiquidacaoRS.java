package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LiquidacaoRS(
        Long id,
        String trackId,
        String status,
        String dsObservacao,
        BigDecimal vlLiquidado,
        BigDecimal vlCambioAplicado,
        String sgMoedaLiquidacao,
        LocalDateTime dtLiquidacao) {}
