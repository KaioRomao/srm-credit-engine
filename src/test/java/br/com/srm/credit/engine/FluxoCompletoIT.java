package br.com.srm.credit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.enums.StatusLiquidacao;
import br.com.srm.credit.engine.repository.LiquidacaoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@DisplayName("Fluxo completo — intake, liquidação assíncrona e extrato")
class FluxoCompletoIT {

    private static final String CNPJ = "11222333000181";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiquidacaoRepository liquidacaoRepository;

    @Test
    @DisplayName("deve liquidar e aparecer no extrato quando o fluxo roda de ponta a ponta")
    void deveLiquidarEAparecerNoExtratoQuandoFluxoRodaDePontaAPonta() throws Exception {
        Long precificacaoId = criarLoteEObterPrecificacaoId("IT-" + UUID.randomUUID());

        String trackId = UUID.randomUUID().toString();
        MvcResult aceito = mockMvc.perform(post("/api/v1/liquidacoes")
                        .header("TrackId", trackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precificacaoId\":%d,\"sgMoedaLiquidacao\":\"BRL\"}".formatted(precificacaoId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn();

        Long liquidacaoId = extrairId(aceito);

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Liquidacao liquidacao =
                            liquidacaoRepository.findById(liquidacaoId).orElseThrow();
                    assertThat(liquidacao.getStatus()).isEqualTo(StatusLiquidacao.LIQUIDADA);
                });

        mockMvc.perform(get("/api/v1/liquidacoes/" + liquidacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIQUIDADA"))
                .andExpect(jsonPath("$.vlLiquidado").exists())
                .andExpect(jsonPath("$.dsObservacao").doesNotExist());

        mockMvc.perform(get("/api/v1/liquidacoes/extrato").param("cedenteId", cedenteIdDe(liquidacaoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("deve devolver a mesma liquidação quando o mesmo trackId é reenviado")
    void deveDevolverMesmaLiquidacaoQuandoMesmoTrackIdEhReenviado() throws Exception {
        Long precificacaoId = criarLoteEObterPrecificacaoId("IT-REPLAY-" + UUID.randomUUID());
        String trackId = UUID.randomUUID().toString();
        String corpo = "{\"precificacaoId\":%d,\"sgMoedaLiquidacao\":\"BRL\"}".formatted(precificacaoId);

        Long primeiro = extrairId(mockMvc.perform(post("/api/v1/liquidacoes")
                        .header("TrackId", trackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isAccepted())
                .andReturn());

        Long segundo = extrairId(mockMvc.perform(post("/api/v1/liquidacoes")
                        .header("TrackId", trackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isAccepted())
                .andReturn());

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(liquidacaoRepository.findAll().stream()
                        .filter(l -> l.getTrackId().equals(UUID.fromString(trackId)))
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("deve retornar 409 quando a mesma precificação é liquidada com outro trackId")
    void deveRetornar409QuandoMesmaPrecificacaoEhLiquidadaComOutroTrackId() throws Exception {
        Long precificacaoId = criarLoteEObterPrecificacaoId("IT-409-" + UUID.randomUUID());
        String corpo = "{\"precificacaoId\":%d,\"sgMoedaLiquidacao\":\"BRL\"}".formatted(precificacaoId);

        mockMvc.perform(post("/api/v1/liquidacoes")
                        .header("TrackId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/liquidacoes")
                        .header("TrackId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("deve fazer rollback do lote inteiro quando um recebível é inválido")
    void deveFazerRollbackDoLoteInteiroQuandoUmRecebivelEhInvalido() throws Exception {
        long lotesAntes = contarLotes();

        mockMvc.perform(post("/api/v1/lotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dsReferencia":"IT rollback","cedenteDocumento":"%s","cedenteNome":"ACME LTDA",
                                 "vlTaxaBase":0.01,"recebiveis":[
                                   {"nrTitulo":"OK","vlFace":1000.00,"dtVencimento":"%s",
                                    "tipoRecebivel":"DUPLICATA_MERCANTIL","sgMoeda":"BRL","sgMoedaPagamento":"BRL"},
                                   {"nrTitulo":"RUIM","vlFace":2000.00,"dtVencimento":"%s",
                                    "tipoRecebivel":"NOTA_PROMISSORIA","sgMoeda":"BRL","sgMoedaPagamento":"BRL"}]}
                                """
                                        .formatted(CNPJ, vencimento(), vencimento())))
                .andExpect(status().isUnprocessableEntity());

        assertThat(contarLotes())
                .as("tudo ou nada: o primeiro recebível não pode sobreviver à falha do segundo")
                .isEqualTo(lotesAntes);
    }

    @Test
    @DisplayName("deve expor Swagger e OpenAPI quando a aplicação está no ar")
    void deveExporSwaggerEOpenApiQuandoAplicacaoEstaNoAr() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/api/v1/liquidacoes/extrato']").exists());
    }

    private Long criarLoteEObterPrecificacaoId(String nrTitulo) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/lotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dsReferencia":"IT lote","cedenteDocumento":"%s","cedenteNome":"ACME LTDA",
                                 "vlTaxaBase":0.01,"recebiveis":[
                                   {"nrTitulo":"%s","vlFace":10000.00,"dtVencimento":"%s",
                                    "tipoRecebivel":"DUPLICATA_MERCANTIL","sgMoeda":"BRL","sgMoedaPagamento":"BRL"}]}
                                """
                                        .formatted(CNPJ, nrTitulo, vencimento())))
                .andExpect(status().isCreated())
                .andReturn();

        return Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                        resultado.getResponse().getContentAsString(), "$.itens[0].precificacaoId")
                .toString());
    }

    private static Long extrairId(MvcResult resultado) throws Exception {
        return Long.valueOf(
                com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")
                        .toString());
    }

    private String cedenteIdDe(Long liquidacaoId) {
        return liquidacaoRepository
                .findById(liquidacaoId)
                .orElseThrow()
                .getCedente()
                .getId()
                .toString();
    }

    private long contarLotes() {
        List<Liquidacao> todas = liquidacaoRepository.findAll();
        return todas.size();
    }

    private static String vencimento() {
        return LocalDate.now().plusDays(87).toString();
    }
}
