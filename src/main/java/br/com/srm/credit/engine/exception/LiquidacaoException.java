package br.com.srm.credit.engine.exception;

public class LiquidacaoException extends RuntimeException implements ErroDeNegocio {

    public LiquidacaoException(String message) {
        super(message);
    }
}
