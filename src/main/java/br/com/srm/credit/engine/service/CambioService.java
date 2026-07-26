package br.com.srm.credit.engine.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srm.credit.engine.entity.Cambio;

public interface CambioService {

    Cambio sincronizar(LocalDate data, String sgMoedaOrigem, String sgMoedaDestino);

    BigDecimal buscarUltimaCotacao(String sgMoedaOrigem, String sgMoedaDestino);

    BigDecimal converter(BigDecimal valor, String sgMoedaDe, String sgMoedaPara);
}
