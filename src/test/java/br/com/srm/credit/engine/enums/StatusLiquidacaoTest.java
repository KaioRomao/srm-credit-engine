package br.com.srm.credit.engine.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("StatusLiquidacao — máquina de estados")
class StatusLiquidacaoTest {

    @ParameterizedTest(name = "{0} -> {1} deve ser permitida")
    @CsvSource({
        "PENDENTE, PROCESSANDO",
        "PENDENTE, CANCELADA",
        "PENDENTE, FALHA",
        "PROCESSANDO, LIQUIDADA",
        "PROCESSANDO, FALHA"
    })
    void devePermitirTransicaoQuandoEstaNaTabela(StatusLiquidacao origem, StatusLiquidacao destino) {
        boolean permitida = origem.podeTransicionarPara(destino);

        assertThat(permitida).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser rejeitada")
    @CsvSource({
        "PENDENTE, LIQUIDADA",
        "PENDENTE, PENDENTE",
        "PROCESSANDO, PENDENTE",
        "PROCESSANDO, PROCESSANDO",
        "PROCESSANDO, CANCELADA",
        "LIQUIDADA, PROCESSANDO",
        "LIQUIDADA, FALHA",
        "LIQUIDADA, CANCELADA",
        "FALHA, PROCESSANDO",
        "FALHA, LIQUIDADA",
        "CANCELADA, PROCESSANDO",
        "CANCELADA, LIQUIDADA"
    })
    void deveRejeitarTransicaoQuandoNaoEstaNaTabela(StatusLiquidacao origem, StatusLiquidacao destino) {
        boolean permitida = origem.podeTransicionarPara(destino);

        assertThat(permitida).isFalse();
    }

    @ParameterizedTest(name = "{0} não deve aceitar destino nulo")
    @EnumSource(StatusLiquidacao.class)
    void deveRejeitarTransicaoQuandoDestinoEhNulo(StatusLiquidacao origem) {
        boolean permitida = origem.podeTransicionarPara(null);

        assertThat(permitida).isFalse();
    }

    @ParameterizedTest(name = "{0} deve ser terminal")
    @EnumSource(
            value = StatusLiquidacao.class,
            names = {"LIQUIDADA", "FALHA", "CANCELADA"})
    void deveSerTerminalQuandoEstadoEhFinal(StatusLiquidacao status) {
        assertThat(status.isTerminal()).isTrue();
    }

    @ParameterizedTest(name = "{0} não deve ser terminal")
    @EnumSource(
            value = StatusLiquidacao.class,
            names = {"PENDENTE", "PROCESSANDO"})
    void naoDeveSerTerminalQuandoEstadoAindaEvolui(StatusLiquidacao status) {
        assertThat(status.isTerminal()).isFalse();
    }

    @ParameterizedTest(name = "{0} terminal não deve permitir nenhuma saída")
    @EnumSource(
            value = StatusLiquidacao.class,
            names = {"LIQUIDADA", "FALHA", "CANCELADA"})
    void naoDevePermitirNenhumaSaidaQuandoEstadoEhTerminal(StatusLiquidacao terminal) {
        for (StatusLiquidacao destino : StatusLiquidacao.values()) {
            assertThat(terminal.podeTransicionarPara(destino))
                    .as("%s -> %s", terminal, destino)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("deve manter os cinco estados do CHECK constraint do banco")
    void deveManterOsCincoEstadosDoCheckConstraint() {
        assertThat(StatusLiquidacao.values())
                .containsExactly(
                        StatusLiquidacao.PENDENTE,
                        StatusLiquidacao.PROCESSANDO,
                        StatusLiquidacao.LIQUIDADA,
                        StatusLiquidacao.FALHA,
                        StatusLiquidacao.CANCELADA);
    }
}
