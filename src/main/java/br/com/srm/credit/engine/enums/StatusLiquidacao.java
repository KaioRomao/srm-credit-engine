package br.com.srm.credit.engine.enums;

public enum StatusLiquidacao {
    PENDENTE,
    PROCESSANDO,
    LIQUIDADA,
    FALHA,
    CANCELADA;

    public boolean podeTransicionarPara(StatusLiquidacao destino) {
        return switch (this) {
            case PENDENTE -> destino == PROCESSANDO || destino == FALHA || destino == CANCELADA;
            case PROCESSANDO -> destino == LIQUIDADA || destino == FALHA;
            default -> false;
        };
    }

    public boolean isTerminal() {
        return this == LIQUIDADA || this == FALHA || this == CANCELADA;
    }
}
