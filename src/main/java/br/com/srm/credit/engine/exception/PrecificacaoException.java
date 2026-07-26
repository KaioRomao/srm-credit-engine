package br.com.srm.credit.engine.exception;

public class PrecificacaoException extends RuntimeException implements ErroDeNegocio {

    public PrecificacaoException(String message) {
        super(message);
    }
}
