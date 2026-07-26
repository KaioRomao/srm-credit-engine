package br.com.srm.credit.engine.service.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.srm.credit.engine.service.PrecificacaoStrategy;

public class ChequePreDatadoStrategy implements PrecificacaoStrategy {

    private static final BigDecimal SPREAD = new BigDecimal("0.025");

    @Override
    public BigDecimal calcular(BigDecimal vlFace, BigDecimal vlTaxaBase, long qtPrazoDias) {
        BigDecimal fator = BigDecimal.ONE.add(vlTaxaBase).add(SPREAD);
        double prazoMeses = qtPrazoDias / 30.0;
        BigDecimal fatorElevado = BigDecimal.valueOf(Math.pow(fator.doubleValue(), prazoMeses));
        return vlFace.divide(fatorElevado, 4, RoundingMode.HALF_EVEN);
    }

    @Override
    public BigDecimal getSpread() {
        return SPREAD;
    }
}
