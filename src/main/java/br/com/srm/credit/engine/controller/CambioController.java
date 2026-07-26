package br.com.srm.credit.engine.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.credit.engine.dto.rs.CotacaoRS;
import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.mapper.CambioMapper;
import br.com.srm.credit.engine.service.CambioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cambios")
@RequiredArgsConstructor
@Tag(name = "Câmbio", description = "Cotações via API Frankfurter, usadas nas conversões cross-currency")
public class CambioController {

    private final CambioService cambioService;
    private final CambioMapper cambioMapper;

    @PostMapping("/sincronizar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Sincroniza a cotação de um par de moedas",
            description =
                    """
                    Busca a cotação na API Frankfurter para a data e o par informados e persiste. \
                    É pré-requisito das operações cross-currency: sem cotação sincronizada, a \
                    precificação e a liquidação entre moedas diferentes falham.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cotação sincronizada e persistida"),
        @ApiResponse(
                responseCode = "400",
                description = "Parâmetro obrigatório ausente ou data em formato inválido",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Moeda inexistente ou par sem cotação na Frankfurter",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public CotacaoRS sincronizar(
            @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @Parameter(description = "Data de fechamento da cotação (ISO)", example = "2026-07-24")
                    LocalDate data,
            @RequestParam @Parameter(description = "Sigla da moeda de origem", example = "BRL")
                    String sgMoedaCambioOrigem,
            @RequestParam @Parameter(description = "Sigla da moeda de destino", example = "USD")
                    String sgMoedaCambioDestino) {
        return cambioMapper.toCotacaoRS(cambioService.sincronizar(data, sgMoedaCambioOrigem, sgMoedaCambioDestino));
    }

    @GetMapping
    @Operation(
            summary = "Consulta a última cotação de um par",
            description =
                    "Devolve a taxa mais recente por `dt_fechamento`. Aplica taxa inversa quando o par foi sincronizado no sentido oposto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Taxa encontrada"),
        @ApiResponse(
                responseCode = "422",
                description = "Nenhuma cotação sincronizada para o par",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public BigDecimal consultarUltima(
            @RequestParam @Parameter(description = "Sigla da moeda de origem", example = "USD") String origem,
            @RequestParam @Parameter(description = "Sigla da moeda de destino", example = "BRL") String destino) {
        return cambioService.buscarUltimaCotacao(origem, destino);
    }
}
