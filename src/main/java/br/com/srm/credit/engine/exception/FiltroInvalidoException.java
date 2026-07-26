package br.com.srm.credit.engine.exception;

public class FiltroInvalidoException extends RuntimeException implements ErroDeNegocio {

    public FiltroInvalidoException(String message) {
        super(message);
    }
}
