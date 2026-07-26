package br.com.srm.credit.engine.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.credit.engine.dto.rq.SimulacaoPrecificacaoRQ;
import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.dto.rs.PrecificacaoRS;
import br.com.srm.credit.engine.service.PrecificacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/precificacoes")
@RequiredArgsConstructor
@Tag(name = "Precificação", description = "Cálculo do deságio (valor presente) por tipo de recebível")
public class PrecificacaoController {

    private final PrecificacaoService precificacaoService;

    @PostMapping("/simular")
    @Operation(
            summary = "Simula o valor líquido de um recebível",
            description =
                    """
                    Aplica `VP = VF / (1 + TaxaBase + Spread)^Prazo`, com prazo em meses e spread \
                    mensal definido pela Strategy do tipo: duplicata mercantil 1,5% a.m., cheque \
                    pré-datado 2,5% a.m. Quando `sgMoedaPagamento` difere de `sgMoeda`, devolve \
                    também o valor convertido. **Não persiste nada.**""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Simulação calculada"),
        @ApiResponse(
                responseCode = "400",
                description = "Payload inválido",
                content = @Content(schema = @Schema(implementation = ErroRS.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Prazo inválido (vencimento não futuro) ou tipo de recebível sem strategy",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public ResponseEntity<PrecificacaoRS> simular(@Valid @RequestBody SimulacaoPrecificacaoRQ simulacaoPrecificacaoRQ) {
        return ResponseEntity.ok(precificacaoService.simular(simulacaoPrecificacaoRQ));
    }
}
