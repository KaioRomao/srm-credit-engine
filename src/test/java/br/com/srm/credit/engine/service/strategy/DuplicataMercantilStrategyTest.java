package br.com.srm.credit.engine.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srm.credit.engine.service.PrecificacaoStrategy;

@DisplayName("DuplicataMercantilStrategy")
class DuplicataMercantilStrategyTest {

    private static final BigDecimal TAXA_BASE = new BigDecimal("0.01");

    private final PrecificacaoStrategy strategy = new DuplicataMercantilStrategy();

    @Test
    @DisplayName("deve expor spread de 1,5% ao mês")
    void deveExporSpreadDeUmVirgulaCincoPorCento() {
        BigDecimal spread = strategy.getSpread();

        assertThat(spread).isEqualByComparingTo("0.015");
    }

    @Test
    @DisplayName("deve calcular valor presente conhecido quando prazo é de 87 dias")
    void deveCalcularValorPresenteConhecidoQuandoPrazoEhDe87Dias() {
        BigDecimal vlFace = new BigDecimal("10000.00");

        BigDecimal vlLiquido = strategy.calcular(vlFace, TAXA_BASE, 87);

        assertThat(vlLiquido).isEqualByComparingTo("9308.9520");
    }

    @Test
    @DisplayName("deve aplicar deságio maior quando prazo é mais longo")
    void deveAplicarDesagioMaiorQuandoPrazoEhMaisLongo() {
        BigDecimal vlFace = new BigDecimal("10000.00");

        BigDecimal curto = strategy.calcular(vlFace, TAXA_BASE, 30);
        BigDecimal longo = strategy.calcular(vlFace, TAXA_BASE, 180);

        assertThat(longo).isLessThan(curto);
    }

    @Test
    @DisplayName("deve retornar valor menor que o de face quando há deságio")
    void deveRetornarValorMenorQueODeFaceQuandoHaDesagio() {
        BigDecimal vlFace = new BigDecimal("10000.00");

        BigDecimal vlLiquido = strategy.calcular(vlFace, TAXA_BASE, 60);

        assertThat(vlLiquido).isLessThan(vlFace);
    }

    @Test
    @DisplayName("deve aplicar deságio maior quando taxa base é maior")
    void deveAplicarDesagioMaiorQuandoTaxaBaseEhMaior() {
        BigDecimal vlFace = new BigDecimal("10000.00");

        BigDecimal taxaBaixa = strategy.calcular(vlFace, new BigDecimal("0.01"), 90);
        BigDecimal taxaAlta = strategy.calcular(vlFace, new BigDecimal("0.05"), 90);

        assertThat(taxaAlta).isLessThan(taxaBaixa);
    }

    @Test
    @DisplayName("deve devolver resultado com quatro casas decimais")
    void deveDevolverResultadoComQuatroCasasDecimais() {
        BigDecimal vlLiquido = strategy.calcular(new BigDecimal("10000.00"), TAXA_BASE, 87);

        assertThat(vlLiquido.scale()).isEqualTo(4);
    }
}
