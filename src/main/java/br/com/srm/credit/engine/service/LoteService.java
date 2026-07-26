package br.com.srm.credit.engine.service;

import br.com.srm.credit.engine.dto.rq.LoteRQ;
import br.com.srm.credit.engine.dto.rs.LoteRS;

public interface LoteService {

    LoteRS criar(LoteRQ loteRQ);
}
