package br.com.srm.credit.engine.exception;

public class RecursoNaoEncontradoException extends RuntimeException implements ErroDeNegocio {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
