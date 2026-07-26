package br.com.srm.credit.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.credit.engine.dto.rs.LoteRS;
import br.com.srm.credit.engine.dto.rs.LoteRS.PrecificacaoItemRS;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.GlobalExceptionHandler;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.service.LoteService;

@WebMvcTest(LoteController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("LoteController")
class LoteControllerTest {

    private static final String ROTA = "/api/v1/lotes";
    private static final String CNPJ = "11222333000181";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoteService loteService;

    @Test
    @DisplayName("deve retornar 201 com os itens precificados quando o lote é válido")
    void deveRetornar201ComItensPrecificadosQuandoLoteEhValido() throws Exception {
        when(loteService.criar(any())).thenReturn(loteCriado());

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComUmItem("10000.00", "2026-10-20", "DUPLICATA_MERCANTIL", "BRL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].vlLiquido").value(9308.9520));
    }

    @Test
    @DisplayName("deve retornar 400 quando a lista de recebíveis está vazia")
    void deveRetornar400QuandoListaDeRecebiveisEstaVazia() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dsReferencia":"Lote","cedenteDocumento":"%s","cedenteNome":"ACME",
                                 "vlTaxaBase":0.01,"recebiveis":[]}
                                """
                                        .formatted(CNPJ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("recebiveis"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("informe ao menos um recebível"));
    }

    @Test
    @DisplayName("deve retornar 400 com path indexado quando o campo inválido está no item aninhado")
    void deveRetornar400ComPathIndexadoQuandoCampoInvalidoEstaNoItemAninhado() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComUmItem("-5.00", "2026-10-20", "DUPLICATA_MERCANTIL", "BRL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("recebiveis[0].vlFace"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o vencimento do item está no passado")
    void deveRetornar400QuandoVencimentoDoItemEstaNoPassado() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComUmItem("1000.00", "2020-01-01", "DUPLICATA_MERCANTIL", "BRL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("recebiveis[0].dtVencimento"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o documento do cedente está em branco")
    void deveRetornar400QuandoDocumentoDoCedenteEstaEmBranco() throws Exception {
        mockMvc.perform(
                        post(ROTA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dsReferencia":"Lote","cedenteDocumento":"","cedenteNome":"ACME","vlTaxaBase":0.01,
                                 "recebiveis":[{"nrTitulo":"T1","vlFace":1000.00,"dtVencimento":"2026-10-20",
                                 "tipoRecebivel":"DUPLICATA_MERCANTIL","sgMoeda":"BRL","sgMoedaPagamento":"BRL"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("cedenteDocumento"));
    }

    @Test
    @DisplayName("deve retornar 400 quando a taxa base está ausente")
    void deveRetornar400QuandoTaxaBaseEstaAusente() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dsReferencia":"Lote","cedenteDocumento":"%s","cedenteNome":"ACME",
                                 "recebiveis":[{"nrTitulo":"T1","vlFace":1000.00,"dtVencimento":"2026-10-20",
                                 "tipoRecebivel":"DUPLICATA_MERCANTIL","sgMoeda":"BRL","sgMoedaPagamento":"BRL"}]}
                                """
                                        .formatted(CNPJ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("vlTaxaBase"));
    }

    @Test
    @DisplayName("deve retornar 422 quando o tipo de recebível não existe")
    void deveRetornar422QuandoTipoDeRecebivelNaoExiste() throws Exception {
        when(loteService.criar(any()))
                .thenThrow(new PrecificacaoException("Tipo de recebível não encontrado: NOTA_PROMISSORIA"));

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComUmItem("1000.00", "2026-10-20", "NOTA_PROMISSORIA", "BRL")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Tipo de recebível não encontrado: NOTA_PROMISSORIA"));
    }

    @Test
    @DisplayName("deve retornar 422 quando a moeda do recebível não existe")
    void deveRetornar422QuandoMoedaDoRecebivelNaoExiste() throws Exception {
        when(loteService.criar(any())).thenThrow(new CambioException("Moeda não encontrada: XYZ"));

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComUmItem("1000.00", "2026-10-20", "DUPLICATA_MERCANTIL", "XYZ")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deve retornar 415 quando o content type não é JSON")
    void deveRetornar415QuandoContentTypeNaoEhJson() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.TEXT_PLAIN).content("oi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static String payloadComUmItem(String vlFace, String dtVencimento, String tipo, String sgMoeda) {
        return """
                {"dsReferencia":"Lote teste","cedenteDocumento":"%s","cedenteNome":"ACME LTDA","vlTaxaBase":0.01,
                 "recebiveis":[{"nrTitulo":"T1","vlFace":%s,"dtVencimento":"%s",
                 "tipoRecebivel":"%s","sgMoeda":"%s","sgMoedaPagamento":"%s"}]}
                """
                .formatted(CNPJ, vlFace, dtVencimento, tipo, sgMoeda, sgMoeda);
    }

    private static LoteRS loteCriado() {
        PrecificacaoItemRS item = new PrecificacaoItemRS(
                1L,
                1L,
                "T1",
                new BigDecimal("10000.00"),
                new BigDecimal("9308.9520"),
                new BigDecimal("9308.9520"),
                87,
                "BRL",
                "BRL");
        return new LoteRS(1L, "Lote teste", "ACME LTDA", CNPJ, LocalDateTime.of(2026, 7, 25, 10, 0), List.of(item));
    }
}
