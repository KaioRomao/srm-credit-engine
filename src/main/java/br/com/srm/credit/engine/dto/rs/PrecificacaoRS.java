package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;

public record PrecificacaoRS(
        BigDecimal vlFace,
        BigDecimal vlLiquido,
        BigDecimal vlConvertido,
        Integer qtPrazoDia,
        String tipoRecebivel,
        String sgMoeda,
        String sgMoedaPagamento) {}
