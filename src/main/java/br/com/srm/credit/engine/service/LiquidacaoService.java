package br.com.srm.credit.engine.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.com.srm.credit.engine.dto.rq.LiquidacaoFiltroRQ;
import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;

public interface LiquidacaoService {

    LiquidacaoRS iniciaLiquidacao(UUID trackId, LiquidacaoRQ liquidacaoRQ);

    LiquidacaoRS consultaLiquidacao(Long liquidacaoId);

    Page<LiquidacaoRS> listaLiquidacoes(LiquidacaoFiltroRQ filtro, Pageable pageable);

    void processaLiquidacao(Long liquidacaoId);

    void finalizaLiquidacao(Long liquidacaoId);

    void registrarFalha(Long liquidacaoId, Throwable causa);
}
