package br.com.srm.credit.engine.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.dto.rs.ErroRS.CampoErroRS;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErroRS> tratarValidacao(BindException e, HttpServletRequest request) {
        List<CampoErroRS> campos = new ArrayList<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(erro -> campos.add(new CampoErroRS(erro.getField(), erro.getDefaultMessage())));
        e.getBindingResult()
                .getGlobalErrors()
                .forEach(erro -> campos.add(new CampoErroRS(erro.getObjectName(), erro.getDefaultMessage())));
        return montar(HttpStatus.BAD_REQUEST, "Falha de validação na requisição", request, campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroRS> tratarConstraintViolada(ConstraintViolationException e, HttpServletRequest request) {
        List<CampoErroRS> campos = e.getConstraintViolations().stream()
                .map(violacao -> new CampoErroRS(String.valueOf(violacao.getPropertyPath()), violacao.getMessage()))
                .toList();
        return montar(HttpStatus.BAD_REQUEST, "Falha de validação na requisição", request, campos);
    }

    @ExceptionHandler({
        FiltroInvalidoException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErroRS> tratarRequisicaoMalformada(Exception e, HttpServletRequest request) {
        return montar(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroRS> tratarArgumentoInvalido(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Argumento inválido em {}: {}", request.getRequestURI(), e.getMessage());
        return montar(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler({
        RecursoNaoEncontradoException.class,
        EntityNotFoundException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ErroRS> tratarNaoEncontrado(Exception e, HttpServletRequest request) {
        return montar(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroRS> tratarMetodoNaoSuportado(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return montar(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroRS> tratarMediaTypeNaoSuportado(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return montar(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage(), request);
    }

    @ExceptionHandler({
        ConflitoNegocioException.class,
        OptimisticLockingFailureException.class,
        OptimisticLockException.class,
        DataIntegrityViolationException.class
    })
    public ResponseEntity<ErroRS> tratarConflito(Exception e, HttpServletRequest request) {
        log.warn("Conflito em {}: {}", request.getRequestURI(), e.getMessage());
        String mensagem = (e instanceof ConflitoNegocioException)
                ? e.getMessage()
                : "Conflito de concorrência ou violação de unicidade ao gravar o recurso";
        return montar(HttpStatus.CONFLICT, mensagem, request);
    }

    @ExceptionHandler({PrecificacaoException.class, CambioException.class, LiquidacaoException.class})
    public ResponseEntity<ErroRS> tratarRegraDeNegocio(RuntimeException e, HttpServletRequest request) {
        return montar(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroRS> tratarErroInesperado(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado em {}", request.getRequestURI(), e);
        return montar(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request);
    }

    private ResponseEntity<ErroRS> montar(HttpStatus status, String mensagem, HttpServletRequest request) {
        return montar(status, mensagem, request, null);
    }

    private ResponseEntity<ErroRS> montar(
            HttpStatus status, String mensagem, HttpServletRequest request, List<CampoErroRS> campos) {
        ErroRS corpo = new ErroRS(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI(),
                campos);
        return ResponseEntity.status(status).body(corpo);
    }
}
