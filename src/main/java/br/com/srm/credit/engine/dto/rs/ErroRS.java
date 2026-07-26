package br.com.srm.credit.engine.dto.rs;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroRS(
        LocalDateTime timestamp, int status, String error, String message, String path, List<CampoErroRS> erros) {

    public record CampoErroRS(String campo, String mensagem) {}
}
