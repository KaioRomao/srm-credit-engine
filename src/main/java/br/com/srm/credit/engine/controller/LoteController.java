package br.com.srm.credit.engine.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.credit.engine.dto.rq.LoteRQ;
import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.dto.rs.LoteRS;
import br.com.srm.credit.engine.service.LoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/lotes")
@RequiredArgsConstructor
@Tag(name = "Lote", description = "Entrada de recebíveis e precificação persistida")
public class LoteController {

    private final LoteService loteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cria um lote de recebíveis",
            description =
                    """
                    Resolve o cedente por `cedenteDocumento` (cria se não existir), grava o lote e, \
                    para cada recebível, persiste a precificação congelando spread, taxa base e prazo. \
                    Operação tudo-ou-nada: falha em um recebível faz rollback do lote inteiro.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Lote criado com os itens precificados"),
        @ApiResponse(
                responseCode = "400",
                description = "Payload inválido (campo obrigatório, CNPJ inválido, vencimento no passado)",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "422",
                description =
                        "Regra de negócio: tipo de recebível ou moeda inexistente, câmbio sem cotação sincronizada",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public LoteRS criar(@Valid @RequestBody LoteRQ loteRQ) {
        return loteService.criar(loteRQ);
    }
}
