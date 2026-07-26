package br.com.srm.credit.engine.service;

import java.math.BigDecimal;

public interface PrecificacaoStrategy {

    BigDecimal calcular(BigDecimal vlFace, BigDecimal vlTaxaBase, long qtPrazoDias);

    BigDecimal getSpread();
}
