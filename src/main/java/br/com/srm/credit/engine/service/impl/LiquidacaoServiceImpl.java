package br.com.srm.credit.engine.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;
import br.com.srm.credit.engine.dto.rq.LiquidacaoFiltroRQ;
import br.com.srm.credit.engine.dto.rq.LiquidacaoRQ;
import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.ConflitoNegocioException;
import br.com.srm.credit.engine.exception.ErroDeNegocio;
import br.com.srm.credit.engine.exception.FiltroInvalidoException;
import br.com.srm.credit.engine.exception.LiquidacaoException;
import br.com.srm.credit.engine.exception.RecursoNaoEncontradoException;
import br.com.srm.credit.engine.mapper.LiquidacaoMapper;
import br.com.srm.credit.engine.repository.LiquidacaoRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.PrecificacaoRepository;
import br.com.srm.credit.engine.service.CambioService;
import br.com.srm.credit.engine.service.LiquidacaoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LiquidacaoServiceImpl implements LiquidacaoService {

    private static final Logger log = LoggerFactory.getLogger(LiquidacaoServiceImpl.class);

    private static final int TAMANHO_MAX_OBSERVACAO = 500;
    private static final int TAMANHO_MAX_PAGINA = 100;
    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("id", "trackId", "status", "vlLiquidado", "dtCriacao", "dtLiquidacao");
    private static final String MENSAGEM_FALHA_INESPERADA =
            "Falha inesperada no processamento. Acione o suporte informando o trackId.";

    private final LiquidacaoRepository liquidacaoRepository;
    private final PrecificacaoRepository precificacaoRepository;
    private final MoedaRepository moedaRepository;
    private final CambioService cambioService;
    private final LiquidacaoMapper liquidacaoMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public LiquidacaoRS iniciaLiquidacao(UUID trackId, LiquidacaoRQ liquidacaoRQ) {
        Optional<Liquidacao> existente = liquidacaoRepository.findByTrackId(trackId);
        if (existente.isPresent()) {
            return liquidacaoMapper.toLiquidacaoRS(existente.get());
        }

        Precificacao precificacao = precificacaoRepository
                .findById(liquidacaoRQ.precificacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Precificação não encontrada: " + liquidacaoRQ.precificacaoId()));
        if (liquidacaoRepository.existsByPrecificacaoId(precificacao.getId())) {
            throw new ConflitoNegocioException("Precificação " + precificacao.getId() + " já foi liquidada");
        }
        if (precificacao.getVlLiquido() == null) {
            throw new LiquidacaoException("Precificação " + precificacao.getId() + " não possui valor líquido");
        }

        Moeda moedaLiquidacao = moedaRepository
                .findBySgMoeda(liquidacaoRQ.sgMoedaLiquidacao())
                .orElseThrow(() -> new CambioException("Moeda não encontrada: " + liquidacaoRQ.sgMoedaLiquidacao()));

        Liquidacao liquidacao = criarLiquidacaoPendente(trackId, precificacao, moedaLiquidacao);

        applicationEventPublisher.publishEvent(new LiquidacaoMensagem(liquidacao.getId(), trackId));

        return liquidacaoMapper.toLiquidacaoRS(liquidacao);
    }

    @Override
    @Transactional(readOnly = true)
    public LiquidacaoRS consultaLiquidacao(Long liquidacaoId) {
        return liquidacaoMapper.toLiquidacaoRS(buscar(liquidacaoId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LiquidacaoRS> listaLiquidacoes(LiquidacaoFiltroRQ filtro, Pageable pageable) {
        return liquidacaoRepository
                .buscarPorFiltro(filtro.id(), filtro.trackId(), filtro.status(), validarPaginacao(pageable))
                .map(liquidacaoMapper::toLiquidacaoRS);
    }

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 50,
            multiplier = 2,
            maxDelay = 400,
            jitter = 25)
    @Override
    @Transactional
    public void processaLiquidacao(Long liquidacaoId) {
        Liquidacao liquidacao = buscar(liquidacaoId);
        if (liquidacao.getStatus() != StatusLiquidacao.PENDENTE) {
            log.info(
                    "Liquidacao {} ignorada em processaLiquidacao: status atual {}",
                    liquidacaoId,
                    liquidacao.getStatus());
            return;
        }
        transicionar(liquidacao, StatusLiquidacao.PROCESSANDO);
        liquidacaoRepository.save(liquidacao);
    }

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 50,
            multiplier = 2,
            maxDelay = 400,
            jitter = 25)
    @Override
    @Transactional
    public void finalizaLiquidacao(Long liquidacaoId) {
        Liquidacao liquidacao = buscar(liquidacaoId);
        if (liquidacao.getStatus().isTerminal()) {
            log.info(
                    "Liquidacao {} ignorada em finalizaLiquidacao: já em estado terminal {}",
                    liquidacaoId,
                    liquidacao.getStatus());
            return;
        }

        Precificacao precificacao = liquidacao.getPrecificacao();
        String sgMoedaOrigem = precificacao.getRecebivel().getMoeda().getSgMoeda();
        String sgMoedaDestino = liquidacao.getMoedaLiquidacao().getSgMoeda();

        BigDecimal vlLiquidado = cambioService.converter(precificacao.getVlLiquido(), sgMoedaOrigem, sgMoedaDestino);
        liquidacao.setVlLiquidado(vlLiquidado);
        liquidacao.setVlCambioAplicado(
                calcularTaxaAplicada(precificacao.getVlLiquido(), vlLiquidado, sgMoedaOrigem, sgMoedaDestino));
        transicionar(liquidacao, StatusLiquidacao.LIQUIDADA);
        liquidacao.setDtLiquidacao(LocalDateTime.now());
        liquidacaoRepository.save(liquidacao);
    }

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 50,
            multiplier = 2,
            maxDelay = 400,
            jitter = 25)
    @Override
    @Transactional
    public void registrarFalha(Long liquidacaoId, Throwable causa) {
        Liquidacao liquidacao = buscar(liquidacaoId);

        if (!liquidacao.getStatus().podeTransicionarPara(StatusLiquidacao.FALHA)) {
            log.warn(
                    "Liquidacao {} não pode ir para FALHA a partir de {}; falha não registrada",
                    liquidacaoId,
                    liquidacao.getStatus());
            return;
        }

        String observacao;
        if (causa instanceof ErroDeNegocio) {
            observacao = truncar(causa.getMessage());
        } else {
            log.error("Falha inesperada ao processar liquidacao {}", liquidacaoId, causa);
            observacao = MENSAGEM_FALHA_INESPERADA;
        }

        transicionar(liquidacao, StatusLiquidacao.FALHA);
        liquidacao.setDsObservacao(observacao);
        liquidacaoRepository.save(liquidacao);
    }

    private void transicionar(Liquidacao liquidacao, StatusLiquidacao destino) {
        StatusLiquidacao atual = liquidacao.getStatus();
        if (!atual.podeTransicionarPara(destino)) {
            throw new ConflitoNegocioException(
                    "Transição inválida na liquidação " + liquidacao.getId() + ": " + atual + " -> " + destino);
        }
        liquidacao.setStatus(destino);
        liquidacao.setDtAtualizacao(LocalDateTime.now());
    }

    private Liquidacao criarLiquidacaoPendente(UUID trackId, Precificacao precificacao, Moeda moedaLiquidacao) {
        Liquidacao liquidacao = new Liquidacao();
        liquidacao.setTrackId(trackId);
        liquidacao.setPrecificacao(precificacao);
        liquidacao.setCedente(precificacao.getRecebivel().getCedente());
        liquidacao.setMoedaLiquidacao(moedaLiquidacao);
        liquidacao.setStatus(StatusLiquidacao.PENDENTE);
        liquidacao.setDtCriacao(LocalDateTime.now());
        return liquidacaoRepository.save(liquidacao);
    }

    private Pageable validarPaginacao(Pageable pageable) {
        pageable.getSort().forEach(ordem -> {
            if (!CAMPOS_ORDENACAO.contains(ordem.getProperty())) {
                throw new FiltroInvalidoException("Ordenação não suportada: '" + ordem.getProperty()
                        + "'. Campos permitidos: "
                        + CAMPOS_ORDENACAO.stream().sorted().toList());
            }
        });
        if (pageable.getPageSize() <= TAMANHO_MAX_PAGINA) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), TAMANHO_MAX_PAGINA, pageable.getSort());
    }

    private Liquidacao buscar(Long liquidacaoId) {
        return liquidacaoRepository
                .findById(liquidacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Liquidação não encontrada: " + liquidacaoId));
    }

    private String truncar(String erro) {
        if (erro == null) {
            return null;
        }
        return erro.length() > TAMANHO_MAX_OBSERVACAO ? erro.substring(0, TAMANHO_MAX_OBSERVACAO) : erro;
    }

    private BigDecimal calcularTaxaAplicada(
            BigDecimal vlOrigem, BigDecimal vlDestino, String sgMoedaOrigem, String sgMoedaDestino) {
        if (sgMoedaOrigem.equals(sgMoedaDestino)) {
            return BigDecimal.ONE;
        }
        return vlDestino.divide(vlOrigem, 6, RoundingMode.HALF_EVEN);
    }
}
