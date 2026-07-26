package br.com.srm.credit.engine.service;

import java.math.BigDecimal;

import br.com.srm.credit.engine.dto.rq.SimulacaoPrecificacaoRQ;
import br.com.srm.credit.engine.dto.rs.PrecificacaoRS;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;

public interface PrecificacaoService {

    BigDecimal calcular(Recebivel recebivel, BigDecimal vlTaxaBase);

    PrecificacaoRS simular(SimulacaoPrecificacaoRQ simulacaoPrecificacaoRQ);

    Precificacao precificar(Recebivel recebivel, BigDecimal vlTaxaBase, String sgMoedaPagamento);
}
