package br.com.srm.credit.engine.exception;

public class ConflitoNegocioException extends RuntimeException implements ErroDeNegocio {

    public ConflitoNegocioException(String message) {
        super(message);
    }
}
