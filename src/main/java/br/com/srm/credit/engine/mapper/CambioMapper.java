package br.com.srm.credit.engine.mapper;

import org.springframework.stereotype.Component;

import br.com.srm.credit.engine.dto.rs.CotacaoRS;
import br.com.srm.credit.engine.entity.Cambio;

@Component
public class CambioMapper {

    public CotacaoRS toCotacaoRS(Cambio cambio) {
        return new CotacaoRS(
                cambio.getId(),
                cambio.getMoedaOrigem().getSgMoeda(),
                cambio.getMoedaDestino().getSgMoeda(),
                cambio.getVlCambio(),
                cambio.getDtFechamento());
    }
}
