package br.com.srm.credit.engine.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.credit.engine.dto.rq.LoteRQ;
import br.com.srm.credit.engine.dto.rq.LoteRQ.RecebivelItemRQ;
import br.com.srm.credit.engine.dto.rs.LoteRS;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.mapper.LoteMapper;
import br.com.srm.credit.engine.repository.CedenteRepository;
import br.com.srm.credit.engine.repository.LoteRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.RecebivelRepository;
import br.com.srm.credit.engine.repository.RecebivelTipoRepository;
import br.com.srm.credit.engine.service.LoteService;
import br.com.srm.credit.engine.service.PrecificacaoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoteServiceImpl implements LoteService {

    private final CedenteRepository cedenteRepository;
    private final LoteRepository loteRepository;
    private final RecebivelRepository recebivelRepository;
    private final RecebivelTipoRepository recebivelTipoRepository;
    private final MoedaRepository moedaRepository;
    private final PrecificacaoService precificacaoService;
    private final LoteMapper loteMapper;

    @Override
    @Transactional
    public LoteRS criar(LoteRQ loteRQ) {
        Cedente cedente = resolverCedente(loteRQ);

        Lote lote = new Lote();
        lote.setDsReferencia(loteRQ.dsReferencia());
        lote.setDtCriacao(LocalDateTime.now());
        loteRepository.save(lote);

        List<Precificacao> precificacoes = new ArrayList<>();
        for (RecebivelItemRQ item : loteRQ.recebiveis()) {
            Recebivel recebivel = criarRecebivel(item, cedente, lote);
            precificacoes.add(precificacaoService.precificar(recebivel, loteRQ.vlTaxaBase(), item.sgMoedaPagamento()));
        }

        return loteMapper.toLoteRS(lote, cedente, precificacoes);
    }

    private Cedente resolverCedente(LoteRQ loteRQ) {
        return cedenteRepository.findByNrDocumento(loteRQ.cedenteDocumento()).orElseGet(() -> {
            Cedente novoCedente = new Cedente();
            novoCedente.setNrDocumento(loteRQ.cedenteDocumento());
            novoCedente.setNmCedente(loteRQ.cedenteNome());
            return cedenteRepository.save(novoCedente);
        });
    }

    private Recebivel criarRecebivel(RecebivelItemRQ item, Cedente cedente, Lote lote) {
        Moeda moeda = buscarMoeda(item.sgMoeda());
        RecebivelTipo recebivelTipo = recebivelTipoRepository
                .findByDsRecebivelTipo(item.tipoRecebivel())
                .orElseThrow(
                        () -> new PrecificacaoException("Tipo de recebível não encontrado: " + item.tipoRecebivel()));

        Recebivel recebivel = new Recebivel();
        recebivel.setNrTitulo(item.nrTitulo());
        recebivel.setVlFace(item.vlFace());
        recebivel.setDtVencimento(item.dtVencimento());
        recebivel.setRecebivelTipo(recebivelTipo);
        recebivel.setCedente(cedente);
        recebivel.setMoeda(moeda);
        recebivel.setLote(lote);
        recebivel.setDtCriacao(LocalDateTime.now());

        return recebivelRepository.save(recebivel);
    }

    private Moeda buscarMoeda(String sgMoeda) {
        return moedaRepository
                .findBySgMoeda(sgMoeda)
                .orElseThrow(() -> new CambioException("Moeda não encontrada: " + sgMoeda));
    }
}
