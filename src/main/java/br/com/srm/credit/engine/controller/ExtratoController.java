package br.com.srm.credit.engine.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.credit.engine.dto.rq.ExtratoFiltroRQ;
import br.com.srm.credit.engine.dto.rs.ErroRS;
import br.com.srm.credit.engine.dto.rs.ExtratoItemRS;
import br.com.srm.credit.engine.repository.ExtratoRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/liquidacoes")
@RequiredArgsConstructor
@Tag(name = "Extrato", description = "Relatório analítico de liquidações concluídas")
public class ExtratoController {

    private final ExtratoRepository extratoRepository;

    @GetMapping("/extrato")
    @Operation(
            summary = "Extrato de liquidações com filtros e paginação",
            description =
                    """
                    Lista apenas liquidações LIQUIDADA. Filtros opcionais e combináveis; o período \
                    usa dt_liquidacao e é inclusivo nas duas pontas.

                    Paginação server-side, com size limitado a 100. A ordenação aceita apenas \
                    dtLiquidacao, vlLiquidado, vlFace, dtVencimento, cedenteNome e \
                    sgMoedaLiquidacao (padrão dtLiquidacao,desc); outro campo retorna 400.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página do extrato (vazia se nada casar com os filtros)"),
        @ApiResponse(
                responseCode = "400",
                description = "dataInicio posterior a dataFim, ou campo de ordenação não suportado",
                content = @Content(schema = @Schema(implementation = ErroRS.class)))
    })
    public Page<ExtratoItemRS> consultar(
            @Valid @ModelAttribute ExtratoFiltroRQ filtro,
            @PageableDefault(size = 20, sort = "dtLiquidacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return extratoRepository.buscar(filtro, pageable);
    }
}
