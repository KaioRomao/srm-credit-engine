package br.com.srm.credit.engine.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.credit.engine.dto.rq.LiquidacaoFiltroRQ;
import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;
import br.com.srm.credit.engine.service.LiquidacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/liquidacoes")
@RequiredArgsConstructor
@Tag(name = "Liquidação", description = "Liquidação idempotente, processada de forma assíncrona")
public class LiquidacaoController {

    private final LiquidacaoService liquidacaoService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Solicita a liquidação de uma precificação",
            description =
                    "Responde 202 com a liquidação em PENDENTE; o câmbio e a baixa rodam depois, no consumer da fila. "
                            + "Idempotente pelo header TrackId: repetir a mesma chave devolve a liquidação já criada. "
                            + "Uma precificação só pode ser liquidada uma vez.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Liquidação aceita e enfileirada (ou replay da existente)"),
        @ApiResponse(
                responseCode = "400",
                description = "Header TrackId ausente ou não é um UUID válido",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Precificação inexistente",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Precificação já liquidada, ou conflito de concorrência",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Regra de negócio: moeda inexistente, precificação sem valor líquido",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public LiquidacaoRS solicitar(
            @RequestHeader("TrackId")
                    @Parameter(
                            description = "Chave de idempotência (UUID). Mesma chave devolve a mesma liquidação.",
                            required = true,
                            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                    String trackId,
            @Valid @RequestBody LiquidacaoRQ liquidacaoRQ) {
        return liquidacaoService.iniciaLiquidacao(UUID.fromString(trackId), liquidacaoRQ);
    }

    @GetMapping
    @Operation(
            summary = "Lista as liquidações com filtros e paginação",
            description =
                    """
                    Lista liquidações de qualquer status, com paginação server-side e size limitado a 100. \
                    Filtros opcionais e combináveis: id, trackId e status.

                    A ordenação aceita apenas dtCriacao, dtLiquidacao, id, status, trackId e vlLiquidado \
                    (padrão dtCriacao,desc); outro campo retorna 400.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de liquidações (vazia se nada casar com os filtros)"),
        @ApiResponse(
                responseCode = "400",
                description = "Filtro inválido (id não numérico, trackId que não é UUID, status inexistente) "
                        + "ou campo de ordenação não suportado",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public Page<LiquidacaoRS> listar(
            @Valid @ModelAttribute LiquidacaoFiltroRQ filtro,
            @PageableDefault(size = 20, sort = "dtCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return liquidacaoService.listaLiquidacoes(filtro, pageable);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consulta o estado de uma liquidação",
            description = "Estados possíveis: PENDENTE, PROCESSANDO, LIQUIDADA e FALHA. "
                    + "Em FALHA, o motivo vem em dsObservacao.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liquidação encontrada"),
        @ApiResponse(
                responseCode = "400",
                description = "Id não numérico",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Liquidação inexistente",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public LiquidacaoRS consultar(
            @PathVariable @Parameter(description = "Id da liquidação devolvido pelo POST", example = "1") Long id) {
        return liquidacaoService.consultaLiquidacao(id);
    }
}
