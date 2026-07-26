package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioRS(LocalDate date, String base, String quote, BigDecimal rate) {}
