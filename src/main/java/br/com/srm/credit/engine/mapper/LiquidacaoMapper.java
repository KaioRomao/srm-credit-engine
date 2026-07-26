package br.com.srm.credit.engine.mapper;

import org.springframework.stereotype.Component;

import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;
import br.com.srm.credit.engine.entity.Liquidacao;

@Component
public class LiquidacaoMapper {

    public LiquidacaoRS toLiquidacaoRS(Liquidacao liquidacao) {
        return new LiquidacaoRS(
                liquidacao.getId(),
                liquidacao.getTrackId() != null ? liquidacao.getTrackId().toString() : null,
                liquidacao.getStatus().name(),
                liquidacao.getDsObservacao(),
                liquidacao.getVlLiquidado(),
                liquidacao.getVlCambioAplicado(),
                liquidacao.getMoedaLiquidacao().getSgMoeda(),
                liquidacao.getDtLiquidacao());
    }
}
