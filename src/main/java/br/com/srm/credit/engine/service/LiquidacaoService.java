package br.com.srm.credit.engine.service;

import java.util.UUID;

import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;

public interface LiquidacaoService {

    LiquidacaoRS iniciaLiquidacao(UUID trackId, LiquidacaoRQ liquidacaoRQ);

    LiquidacaoRS consultaLiquidacao(Long liquidacaoId);

    void processaLiquidacao(Long liquidacaoId);

    void finalizaLiquidacao(Long liquidacaoId);

    void registrarFalha(Long liquidacaoId, Throwable causa);
}
