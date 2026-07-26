package br.com.srm.credit.engine.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srm.credit.engine.service.PrecificacaoStrategy;

@DisplayName("ChequePreDatadoStrategy")
class ChequePreDatadoStrategyTest {

    private static final BigDecimal TAXA_BASE = new BigDecimal("0.01");

    private final PrecificacaoStrategy strategy = new ChequePreDatadoStrategy();

    @Test
    @DisplayName("deve expor spread de 2,5% ao mês")
    void deveExporSpreadDeDoisVirgulaCincoPorCento() {
        BigDecimal spread = strategy.getSpread();

        assertThat(spread).isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("deve calcular valor presente conhecido quando prazo é de 87 dias")
    void deveCalcularValorPresenteConhecidoQuandoPrazoEhDe87Dias() {
        BigDecimal vlFace = new BigDecimal("10000.00");

        BigDecimal vlLiquido = strategy.calcular(vlFace, TAXA_BASE, 87);

        assertThat(vlLiquido).isEqualByComparingTo("9050.5086");
    }

    @Test
    @DisplayName("deve aplicar deságio maior que duplicata quando parâmetros são idênticos")
    void deveAplicarDesagioMaiorQueDuplicataQuandoParametrosSaoIdenticos() {
        BigDecimal vlFace = new BigDecimal("10000.00");
        PrecificacaoStrategy duplicata = new DuplicataMercantilStrategy();

        BigDecimal vlCheque = strategy.calcular(vlFace, TAXA_BASE, 87);
        BigDecimal vlDuplicata = duplicata.calcular(vlFace, TAXA_BASE, 87);

        assertThat(vlCheque)
                .as("cheque tem spread maior, logo valor líquido menor")
                .isLessThan(vlDuplicata);
    }

    @Test
    @DisplayName("deve manter proporcionalidade quando valor de face dobra, a menos do arredondamento")
    void deveManterProporcionalidadeQuandoValorDeFaceDobra() {
        BigDecimal simples = strategy.calcular(new BigDecimal("10000.00"), TAXA_BASE, 90);
        BigDecimal dobro = strategy.calcular(new BigDecimal("20000.00"), TAXA_BASE, 90);

        assertThat(dobro)
                .as("o arredondamento em 4 casas acontece por chamada, então 2 x round(x) pode diferir de round(2x)")
                .isCloseTo(
                        simples.multiply(new BigDecimal("2")),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
    }
}
