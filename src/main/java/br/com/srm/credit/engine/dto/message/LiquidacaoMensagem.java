package br.com.srm.credit.engine.dto.message;

import java.util.UUID;

public record LiquidacaoMensagem(Long liquidacaoId, UUID trackId) {}
