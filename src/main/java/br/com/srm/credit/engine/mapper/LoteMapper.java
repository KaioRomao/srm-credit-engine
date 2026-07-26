package br.com.srm.credit.engine.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import br.com.srm.credit.engine.dto.rs.LoteRS;
import br.com.srm.credit.engine.dto.rs.LoteRS.PrecificacaoItemRS;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;

@Component
public class LoteMapper {

    public LoteRS toLoteRS(Lote lote, Cedente cedente, List<Precificacao> precificacoes) {
        List<PrecificacaoItemRS> itens =
                precificacoes.stream().map(this::toItemRS).toList();

        return new LoteRS(
                lote.getId(),
                lote.getDsReferencia(),
                cedente.getNmCedente(),
                cedente.getNrDocumento(),
                lote.getDtCriacao(),
                itens);
    }

    private PrecificacaoItemRS toItemRS(Precificacao precificacao) {
        Recebivel recebivel = precificacao.getRecebivel();
        String sgMoeda = recebivel.getMoeda().getSgMoeda();
        String sgMoedaPagamento = precificacao.getMoedaDestino() != null
                ? precificacao.getMoedaDestino().getSgMoeda()
                : sgMoeda;

        return new PrecificacaoItemRS(
                precificacao.getId(),
                recebivel.getId(),
                recebivel.getNrTitulo(),
                recebivel.getVlFace(),
                precificacao.getVlLiquido(),
                precificacao.getVlConvertido(),
                precificacao.getQtPrazoDia(),
                sgMoeda,
                sgMoedaPagamento);
    }
}
