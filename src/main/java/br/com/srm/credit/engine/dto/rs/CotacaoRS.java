package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CotacaoRS(
        Long id, String sgMoedaOrigem, String sgMoedaDestino, BigDecimal vlCambio, LocalDateTime dtFechamento) {}
