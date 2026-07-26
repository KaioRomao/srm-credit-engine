package br.com.srm.credit.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.credit.engine.dto.rs.LiquidacaoRS;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.ConflitoNegocioException;
import br.com.srm.credit.engine.exception.GlobalExceptionHandler;
import br.com.srm.credit.engine.exception.LiquidacaoException;
import br.com.srm.credit.engine.exception.RecursoNaoEncontradoException;
import br.com.srm.credit.engine.service.LiquidacaoService;

@WebMvcTest(LiquidacaoController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("LiquidacaoController")
class LiquidacaoControllerTest {

    private static final String ROTA = "/api/v1/liquidacoes";
    private static final String TRACK_ID = "11111111-2222-3333-4444-555555555555";
    private static final String CORPO_VALIDO =
            """
            {"precificacaoId": 10, "sgMoedaLiquidacao": "USD"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiquidacaoService liquidacaoService;

    @Test
    @DisplayName("deve retornar 202 quando a liquidação é aceita")
    void deveRetornar202QuandoLiquidacaoEhAceita() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any())).thenReturn(pendente());

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.vlLiquidado").doesNotExist());
    }

    @Test
    @DisplayName("deve retornar 400 quando o header TrackId está ausente")
    void deveRetornar400QuandoHeaderTrackIdEstaAusente() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(ROTA));
    }

    @Test
    @DisplayName("deve retornar 400 quando o TrackId não é um UUID")
    void deveRetornar400QuandoTrackIdNaoEhUuid() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("TrackId", "nao-e-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 400 quando precificacaoId está ausente no corpo")
    void deveRetornar400QuandoPrecificacaoIdEstaAusenteNoCorpo() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sgMoedaLiquidacao\": \"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("precificacaoId"));
    }

    @Test
    @DisplayName("deve retornar 400 quando sgMoedaLiquidacao está em branco")
    void deveRetornar400QuandoSgMoedaLiquidacaoEstaEmBranco() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precificacaoId\": 10, \"sgMoedaLiquidacao\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("sgMoedaLiquidacao"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o JSON está malformado")
    void deveRetornar400QuandoJsonEstaMalformado() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precificacaoId\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 404 quando a precificação não existe")
    void deveRetornar404QuandoPrecificacaoNaoExiste() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any()))
                .thenThrow(new RecursoNaoEncontradoException("Precificação não encontrada: 10"));

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Precificação não encontrada: 10"));
    }

    @Test
    @DisplayName("deve retornar 409 quando a precificação já foi liquidada")
    void deveRetornar409QuandoPrecificacaoJaFoiLiquidada() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any()))
                .thenThrow(new ConflitoNegocioException("Precificação 10 já foi liquidada"));

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Precificação 10 já foi liquidada"));
    }

    @Test
    @DisplayName("deve retornar 422 quando a moeda de liquidação não existe")
    void deveRetornar422QuandoMoedaDeLiquidacaoNaoExiste() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any()))
                .thenThrow(new CambioException("Moeda não encontrada: XYZ"));

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deve retornar 422 quando a precificação não tem valor líquido")
    void deveRetornar422QuandoPrecificacaoNaoTemValorLiquido() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any()))
                .thenThrow(new LiquidacaoException("Precificação 10 não possui valor líquido"));

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deve retornar 500 com mensagem fixa quando ocorre falha inesperada")
    void deveRetornar500ComMensagemFixaQuandoOcorreFalhaInesperada() throws Exception {
        when(liquidacaoService.iniciaLiquidacao(any(UUID.class), any()))
                .thenThrow(new IllegalStateException("detalhe interno sensível"));

        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Erro interno inesperado"));
    }

    @Test
    @DisplayName("deve retornar 415 quando o content type não é JSON")
    void deveRetornar415QuandoContentTypeNaoEhJson() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("TrackId", TRACK_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("qualquer"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("deve retornar 200 quando consulta uma liquidação existente")
    void deveRetornar200QuandoConsultaLiquidacaoExistente() throws Exception {
        when(liquidacaoService.consultaLiquidacao(1L)).thenReturn(liquidada());

        mockMvc.perform(get(ROTA + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIQUIDADA"))
                .andExpect(jsonPath("$.vlLiquidado").value(7447.1616));
    }

    @Test
    @DisplayName("deve retornar 404 quando consulta uma liquidação inexistente")
    void deveRetornar404QuandoConsultaLiquidacaoInexistente() throws Exception {
        when(liquidacaoService.consultaLiquidacao(eq(999L)))
                .thenThrow(new RecursoNaoEncontradoException("Liquidação não encontrada: 999"));

        mockMvc.perform(get(ROTA + "/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deve retornar 400 quando o id da consulta não é numérico")
    void deveRetornar400QuandoIdDaConsultaNaoEhNumerico() throws Exception {
        mockMvc.perform(get(ROTA + "/abc")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 405 quando usa método não suportado na rota")
    void deveRetornar405QuandoUsaMetodoNaoSuportadoNaRota() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isMethodNotAllowed());
    }

    private static LiquidacaoRS pendente() {
        return new LiquidacaoRS(1L, TRACK_ID, "PENDENTE", null, null, null, "USD", null);
    }

    private static LiquidacaoRS liquidada() {
        return new LiquidacaoRS(
                1L,
                TRACK_ID,
                "LIQUIDADA",
                null,
                new BigDecimal("7447.1616"),
                new BigDecimal("1.000000"),
                "BRL",
                LocalDateTime.of(2026, 7, 25, 10, 52, 3));
    }
}
