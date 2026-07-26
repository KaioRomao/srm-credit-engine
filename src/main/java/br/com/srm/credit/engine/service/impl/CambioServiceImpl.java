package br.com.srm.credit.engine.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.credit.engine.client.CambioClient;
import br.com.srm.credit.engine.dto.rs.CambioRS;
import br.com.srm.credit.engine.entity.Cambio;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.repository.CambioRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.service.CambioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CambioServiceImpl implements CambioService {

    private final CambioClient cambioClient;
    private final CambioRepository cambioRepository;
    private final MoedaRepository moedaRepository;

    @Override
    @Transactional
    public Cambio sincronizar(LocalDate data, String sgMoedaOrigem, String sgMoedaDestino) {
        Moeda moedaOrigem = buscarMoeda(sgMoedaOrigem);
        Moeda moedaDestino = buscarMoeda(sgMoedaDestino);
        CambioRS cambioRS = cambioClient.consultarCambio(data, sgMoedaOrigem, sgMoedaDestino);
        LocalDateTime dtFechamento = cambioRS.date().atStartOfDay();

        Cambio cambio = cambioRepository
                .buscarCambioDoFechamento(sgMoedaOrigem, sgMoedaDestino, dtFechamento)
                .orElseGet(Cambio::new);
        cambio.setMoedaOrigem(moedaOrigem);
        cambio.setMoedaDestino(moedaDestino);
        cambio.setVlCambio(cambioRS.rate());
        cambio.setDtFechamento(dtFechamento);

        return cambioRepository.save(cambio);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal buscarUltimaCotacao(String sgMoedaOrigem, String sgMoedaDestino) {
        return cambioRepository
                .buscarUltimoCambio(sgMoedaOrigem, sgMoedaDestino)
                .map(Cambio::getVlCambio)
                .orElseThrow(() ->
                        new CambioException("Cotação não encontrada para " + sgMoedaOrigem + "->" + sgMoedaDestino));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal converter(BigDecimal valor, String sgMoedaOrigem, String sgMoedaDestino) {
        if (sgMoedaOrigem.equals(sgMoedaDestino)) {
            return valor;
        }
        return valor.multiply(determinaTaxa(sgMoedaOrigem, sgMoedaDestino)).setScale(4, RoundingMode.HALF_EVEN);
    }

    private BigDecimal determinaTaxa(String sgMoedaOrigem, String sgMoedaDestino) {
        Optional<Cambio> cambioOrigemDestino = cambioRepository.buscarUltimoCambio(sgMoedaOrigem, sgMoedaDestino);

        if (cambioOrigemDestino.isPresent()) {
            return cambioOrigemDestino.get().getVlCambio();
        }

        Optional<Cambio> cambioDestinoOrigem = cambioRepository.buscarUltimoCambio(sgMoedaDestino, sgMoedaOrigem);

        if (cambioDestinoOrigem.isPresent()) {
            return BigDecimal.ONE.divide(cambioDestinoOrigem.get().getVlCambio(), 10, RoundingMode.HALF_EVEN);
        }

        throw new CambioException("Cotação não encontrada para " + sgMoedaOrigem + "->" + sgMoedaDestino);
    }

    private Moeda buscarMoeda(String sgMoeda) {
        return moedaRepository
                .findBySgMoeda(sgMoeda)
                .orElseThrow(() -> new CambioException("Moeda não encontrada: " + sgMoeda));
    }
}
