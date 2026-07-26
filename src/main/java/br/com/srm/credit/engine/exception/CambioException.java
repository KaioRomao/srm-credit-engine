package br.com.srm.credit.engine.exception;

public class CambioException extends RuntimeException implements ErroDeNegocio {

    public CambioException(String message) {
        super(message);
    }
}
