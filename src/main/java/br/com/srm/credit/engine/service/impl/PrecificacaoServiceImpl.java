package br.com.srm.credit.engine.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.credit.engine.dto.rq.SimulacaoPrecificacaoRQ;
import br.com.srm.credit.engine.dto.rs.PrecificacaoRS;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.service.CambioService;
import br.com.srm.credit.engine.service.PrecificacaoService;
import br.com.srm.credit.engine.service.PrecificacaoStrategy;
import br.com.srm.credit.engine.service.strategy.ChequePreDatadoStrategy;
import br.com.srm.credit.engine.service.strategy.DuplicataMercantilStrategy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrecificacaoServiceImpl implements PrecificacaoService {

    private final CambioService cambioService;
    private final PrecificacaoRepository precificacaoRepository;
    private final MoedaRepository moedaRepository;

    private final Map<String, PrecificacaoStrategy> precificacaoStrategyMap = Map.of(
            "DUPLICATA_MERCANTIL", new DuplicataMercantilStrategy(),
            "CHEQUE_PRE_DATADO", new ChequePreDatadoStrategy());

    @Override
    public BigDecimal calcular(Recebivel recebivel, BigDecimal vlTaxaBase) {
        PrecificacaoStrategy precificacaoStrategy = determinaPrecificacaoStrategy(recebivel);
        return precificacaoStrategy.calcular(recebivel.getVlFace(), vlTaxaBase, recebivel.getQtPrazoDias());
    }

    @Override
    @Transactional
    public Precificacao precificar(Recebivel recebivel, BigDecimal vlTaxaBase, String sgMoedaPagamento) {
        PrecificacaoStrategy precificacaoStrategy = determinaPrecificacaoStrategy(recebivel);
        if (recebivel.getMoeda() == null) {
            throw new PrecificacaoException("Recebível sem moeda de origem: não é possível persistir a precificação");
        }

        String sgMoedaOrigem = recebivel.getMoeda().getSgMoeda();
        String sgMoedaDestino =
                (sgMoedaPagamento == null || sgMoedaPagamento.isBlank()) ? sgMoedaOrigem : sgMoedaPagamento;

        BigDecimal vlLiquido =
                precificacaoStrategy.calcular(recebivel.getVlFace(), vlTaxaBase, recebivel.getQtPrazoDias());
        BigDecimal vlConvertido = cambioService.converter(vlLiquido, sgMoedaOrigem, sgMoedaDestino);

        Precificacao precificacao = new Precificacao();
        precificacao.setRecebivel(recebivel);
        precificacao.setVlLiquido(vlLiquido);
        precificacao.setVlConvertido(vlConvertido);
        precificacao.setQtPrazoDia((int) recebivel.getQtPrazoDias());
        precificacao.setVlSpread(precificacaoStrategy.getSpread());
        precificacao.setVlTaxaBase(vlTaxaBase);
        if (!sgMoedaDestino.equals(sgMoedaOrigem)) {
            precificacao.setMoedaDestino(buscarMoeda(sgMoedaDestino));
        }
        precificacao.setDtCriacao(LocalDateTime.now());

        return precificacaoRepository.save(precificacao);
    }

    @Override
    public PrecificacaoRS simular(SimulacaoPrecificacaoRQ simulacaoPrecificacaoRQ) {
        Recebivel recebivel = montaRecebivel(simulacaoPrecificacaoRQ);
        BigDecimal vlLiquido = calcular(recebivel, simulacaoPrecificacaoRQ.vlTaxaBase());
        BigDecimal vlConvertido = cambioService.converter(
                vlLiquido, simulacaoPrecificacaoRQ.sgMoeda(), simulacaoPrecificacaoRQ.sgMoedaPagamento());

        return new PrecificacaoRS(
                simulacaoPrecificacaoRQ.vlFace(),
                vlLiquido,
                vlConvertido,
                (int) recebivel.getQtPrazoDias(),
                simulacaoPrecificacaoRQ.tipoRecebivel(),
                simulacaoPrecificacaoRQ.sgMoeda(),
                simulacaoPrecificacaoRQ.sgMoedaPagamento());
    }

    private Recebivel montaRecebivel(SimulacaoPrecificacaoRQ simulacaoPrecificacaoRQ) {
        return new Recebivel(
                null,
                null,
                simulacaoPrecificacaoRQ.vlFace(),
                simulacaoPrecificacaoRQ.dtVencimento(),
                new RecebivelTipo(null, simulacaoPrecificacaoRQ.tipoRecebivel()),
                null,
                null,
                null,
                LocalDateTime.now());
    }

    private PrecificacaoStrategy determinaPrecificacaoStrategy(Recebivel recebivel) {
        validaRecebivel(recebivel);

        PrecificacaoStrategy precificacaoStrategy =
                precificacaoStrategyMap.get(recebivel.getRecebivelTipo().getDsRecebivelTipo());
        if (precificacaoStrategy == null) {
            throw new PrecificacaoException("PrecificaçãoStrategy não encontrada!");
        }
        if (recebivel.getQtPrazoDias() <= 0) {
            throw new PrecificacaoException("Prazo inválido: o vencimento deve ser futuro");
        }
        return precificacaoStrategy;
    }

    private Moeda buscarMoeda(String sgMoeda) {
        return moedaRepository
                .findBySgMoeda(sgMoeda)
                .orElseThrow(() -> new CambioException("Moeda não encontrada: " + sgMoeda));
    }

    private static void validaRecebivel(Recebivel recebivel) {
        if (recebivel == null
                || recebivel.getRecebivelTipo() == null
                || recebivel.getVlFace() == null
                || recebivel.getDtVencimento() == null
                || recebivel.getDtCriacao() == null) {
            throw new PrecificacaoException("Recebível incompleto para precificação");
        }
    }
}
