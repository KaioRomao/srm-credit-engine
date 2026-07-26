package br.com.srm.credit.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.credit.engine.dto.rs.PrecificacaoRS;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.GlobalExceptionHandler;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.service.PrecificacaoService;

@WebMvcTest(PrecificacaoController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PrecificacaoController")
class PrecificacaoControllerTest {

    private static final String ROTA = "/api/v1/precificacoes/simular";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrecificacaoService precificacaoService;

    @Test
    @DisplayName("deve retornar 200 com o valor líquido quando o payload é válido")
    void deveRetornar200ComValorLiquidoQuandoPayloadEhValido() throws Exception {
        when(precificacaoService.simular(any()))
                .thenReturn(new PrecificacaoRS(
                        new BigDecimal("10000.00"),
                        new BigDecimal("9308.9520"),
                        new BigDecimal("9308.9520"),
                        87,
                        "DUPLICATA_MERCANTIL",
                        "BRL",
                        "BRL"));

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("10000.00", "2026-10-20", "DUPLICATA_MERCANTIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vlLiquido").value(9308.9520))
                .andExpect(jsonPath("$.qtPrazoDia").value(87));
    }

    @Test
    @DisplayName("deve retornar 400 quando o valor de face é negativo")
    void deveRetornar400QuandoValorDeFaceEhNegativo() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("-500.00", "2026-10-20", "DUPLICATA_MERCANTIL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("vlFace"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o valor de face é zero")
    void deveRetornar400QuandoValorDeFaceEhZero() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("0.00", "2026-10-20", "DUPLICATA_MERCANTIL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].mensagem").value("vlFace deve ser positivo"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o tipo de recebível está em branco")
    void deveRetornar400QuandoTipoDeRecebivelEstaEmBranco() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("10000.00", "2026-10-20", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
    void deveRetornar400QuandoCamposObrigatoriosEstaoAusentes() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    @DisplayName("deve retornar 422 quando o prazo é inválido")
    void deveRetornar422QuandoPrazoEhInvalido() throws Exception {
        when(precificacaoService.simular(any()))
                .thenThrow(new PrecificacaoException("Prazo inválido: o vencimento deve ser futuro"));

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("10000.00", "2020-01-01", "DUPLICATA_MERCANTIL")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Prazo inválido: o vencimento deve ser futuro"));
    }

    @Test
    @DisplayName("deve retornar 422 quando o tipo de recebível não tem strategy")
    void deveRetornar422QuandoTipoDeRecebivelNaoTemStrategy() throws Exception {
        when(precificacaoService.simular(any()))
                .thenThrow(new PrecificacaoException("PrecificaçãoStrategy não encontrada!"));

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("10000.00", "2026-10-20", "NOTA_PROMISSORIA")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deve retornar 422 quando não há cotação para o par cross-currency")
    void deveRetornar422QuandoNaoHaCotacaoParaOParCrossCurrency() throws Exception {
        when(precificacaoService.simular(any())).thenThrow(new CambioException("Cotação não encontrada para BRL->JPY"));

        mockMvc.perform(
                        post(ROTA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"vlFace":10000.00,"dtVencimento":"2026-10-20","tipoRecebivel":"DUPLICATA_MERCANTIL",
                                 "sgMoeda":"BRL","sgMoedaPagamento":"JPY","vlTaxaBase":0.01}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    private static String payload(String vlFace, String dtVencimento, String tipoRecebivel) {
        return """
                {"vlFace":%s,"dtVencimento":"%s","tipoRecebivel":"%s",
                 "sgMoeda":"BRL","sgMoedaPagamento":"BRL","vlTaxaBase":0.01}
                """
                .formatted(vlFace, dtVencimento, tipoRecebivel);
    }
}
