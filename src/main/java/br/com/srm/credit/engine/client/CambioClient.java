package br.com.srm.credit.engine.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.srm.credit.engine.dto.rs.CambioRS;
import br.com.srm.credit.engine.exception.CambioException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CambioClient {

    private final RestClient restClient;

    public CambioRS consultarCambio(LocalDate data, String sgMoedaOrigem, String sgMoedaDestino) {

        CambioRS[] cotacoes = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/rates")
                        .queryParam("base", sgMoedaOrigem)
                        .queryParam("quotes", sgMoedaDestino)
                        .queryParam("date", data)
                        .build())
                .retrieve()
                .body(CambioRS[].class);

        if (cotacoes == null || cotacoes.length == 0) {
            throw new CambioException(
                    "Frankfurter não retornou cotação para " + sgMoedaOrigem + " -> " + sgMoedaDestino);
        }
        return cotacoes[0];
    }
}
