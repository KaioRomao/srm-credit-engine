package br.com.srm.credit.engine.dto.rq;

import java.util.UUID;

import br.com.srm.credit.engine.enums.StatusLiquidacao;

import io.swagger.v3.oas.annotations.media.Schema;

public record LiquidacaoFiltroRQ(
        @Schema(description = "Id da liquidação", example = "1") Long id,
        @Schema(
                        description = "Chave de idempotência informada na criação (UUID)",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                UUID trackId,
        @Schema(description = "Status da liquidação", example = "LIQUIDADA") StatusLiquidacao status) {}
