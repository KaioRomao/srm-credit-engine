package br.com.srm.credit.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.credit.engine.dto.rs.CotacaoRS;
import br.com.srm.credit.engine.entity.Cambio;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.GlobalExceptionHandler;
import br.com.srm.credit.engine.mapper.CambioMapper;
import br.com.srm.credit.engine.service.CambioService;

@WebMvcTest(CambioController.class)
@Import({GlobalExceptionHandler.class, CambioMapper.class})
@DisplayName("CambioController")
class CambioControllerTest {

    private static final String ROTA = "/api/v1/cambios";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CambioService cambioService;

    @Test
    @DisplayName("deve retornar 201 quando a cotação é sincronizada")
    void deveRetornar201QuandoCotacaoEhSincronizada() throws Exception {
        when(cambioService.sincronizar(any(LocalDate.class), any(), any())).thenReturn(cambio());

        mockMvc.perform(post(ROTA + "/sincronizar")
                        .param("data", "2026-07-24")
                        .param("sgMoedaCambioOrigem", "BRL")
                        .param("sgMoedaCambioDestino", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sgMoedaOrigem").value("BRL"))
                .andExpect(jsonPath("$.vlCambio").value(0.196980));
    }

    @Test
    @DisplayName("deve retornar 400 quando o parâmetro data está ausente")
    void deveRetornar400QuandoParametroDataEstaAusente() throws Exception {
        mockMvc.perform(post(ROTA + "/sincronizar")
                        .param("sgMoedaCambioOrigem", "BRL")
                        .param("sgMoedaCambioDestino", "USD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 400 quando a data está em formato inválido")
    void deveRetornar400QuandoDataEstaEmFormatoInvalido() throws Exception {
        mockMvc.perform(post(ROTA + "/sincronizar")
                        .param("data", "24/07/2026")
                        .param("sgMoedaCambioOrigem", "BRL")
                        .param("sgMoedaCambioDestino", "USD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 422 quando a moeda não existe")
    void deveRetornar422QuandoMoedaNaoExiste() throws Exception {
        when(cambioService.sincronizar(any(LocalDate.class), any(), any()))
                .thenThrow(new CambioException("Moeda não encontrada: XYZ"));

        mockMvc.perform(post(ROTA + "/sincronizar")
                        .param("data", "2026-07-24")
                        .param("sgMoedaCambioOrigem", "XYZ")
                        .param("sgMoedaCambioDestino", "USD"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deve retornar 200 com a taxa quando consulta a última cotação")
    void deveRetornar200ComATaxaQuandoConsultaUltimaCotacao() throws Exception {
        when(cambioService.buscarUltimaCotacao("BRL", "USD")).thenReturn(new BigDecimal("0.196980"));

        mockMvc.perform(get(ROTA).param("origem", "BRL").param("destino", "USD"))
                .andExpect(status().isOk())
                .andExpect(content().string("0.196980"));
    }

    @Test
    @DisplayName("deve retornar 422 quando não há cotação para o par consultado")
    void deveRetornar422QuandoNaoHaCotacaoParaOParConsultado() throws Exception {
        when(cambioService.buscarUltimaCotacao("BRL", "JPY"))
                .thenThrow(new CambioException("Cotação não encontrada para BRL->JPY"));

        mockMvc.perform(get(ROTA).param("origem", "BRL").param("destino", "JPY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cotação não encontrada para BRL->JPY"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o parâmetro origem está ausente")
    void deveRetornar400QuandoParametroOrigemEstaAusente() throws Exception {
        mockMvc.perform(get(ROTA).param("destino", "USD")).andExpect(status().isBadRequest());
    }

    private static Cambio cambio() {
        br.com.srm.credit.engine.entity.Moeda brl = new br.com.srm.credit.engine.entity.Moeda();
        brl.setSgMoeda("BRL");
        br.com.srm.credit.engine.entity.Moeda usd = new br.com.srm.credit.engine.entity.Moeda();
        usd.setSgMoeda("USD");

        Cambio cambio = new Cambio();
        cambio.setId(1L);
        cambio.setMoedaOrigem(brl);
        cambio.setMoedaDestino(usd);
        cambio.setVlCambio(new BigDecimal("0.196980"));
        cambio.setDtFechamento(LocalDateTime.of(2026, 7, 24, 0, 0));
        return cambio;
    }

    @SuppressWarnings("unused")
    private static CotacaoRS cotacao() {
        return new CotacaoRS(1L, "BRL", "USD", new BigDecimal("0.196980"), LocalDateTime.of(2026, 7, 24, 0, 0));
    }
}
