package br.com.srm.credit.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.credit.engine.dto.rs.ExtratoItemRS;
import br.com.srm.credit.engine.exception.FiltroInvalidoException;
import br.com.srm.credit.engine.exception.GlobalExceptionHandler;
import br.com.srm.credit.engine.repository.ExtratoRepository;

@WebMvcTest(ExtratoController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ExtratoController")
class ExtratoControllerTest {

    private static final String ROTA = "/api/v1/liquidacoes/extrato";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExtratoRepository extratoRepository;

    @Test
    @DisplayName("deve retornar 200 com a página do extrato quando não há filtros")
    void deveRetornar200ComPaginaDoExtratoQuandoNaoHaFiltros() throws Exception {
        when(extratoRepository.buscar(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(ROTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].liquidacaoId").value(1))
                .andExpect(jsonPath("$.content[0].vlSpread").value(0.015));
    }

    @Test
    @DisplayName("deve retornar 200 com página vazia quando nada casa com os filtros")
    void deveRetornar200ComPaginaVaziaQuandoNadaCasaComOsFiltros() throws Exception {
        when(extratoRepository.buscar(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(ROTA).param("cedenteId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("deve retornar 400 quando dataInicio é posterior a dataFim")
    void deveRetornar400QuandoDataInicioEhPosteriorADataFim() throws Exception {
        mockMvc.perform(get(ROTA).param("dataInicio", "2026-08-01").param("dataFim", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("periodoCoerente"));
    }

    @Test
    @DisplayName("deve retornar 400 quando a data está no formato do locale e não em ISO")
    void deveRetornar400QuandoDataEstaNoFormatoDoLocaleENaoEmIso() throws Exception {
        mockMvc.perform(get(ROTA).param("dataInicio", "25/07/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("dataInicio"));
    }

    @Test
    @DisplayName("deve retornar 400 quando cedenteId não é numérico")
    void deveRetornar400QuandoCedenteIdNaoEhNumerico() throws Exception {
        mockMvc.perform(get(ROTA).param("cedenteId", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 400 quando o campo de ordenação não está na whitelist")
    void deveRetornar400QuandoCampoDeOrdenacaoNaoEstaNaWhitelist() throws Exception {
        when(extratoRepository.buscar(any(), any(Pageable.class)))
                .thenThrow(new FiltroInvalidoException("Ordenação não suportada: 'dsObservacao'"));

        mockMvc.perform(get(ROTA).param("sort", "dsObservacao,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ordenação não suportada: 'dsObservacao'"));
    }

    @Test
    @DisplayName("deve retornar 400 quando a ordenação tenta injetar SQL")
    void deveRetornar400QuandoOrdenacaoTentaInjetarSql() throws Exception {
        when(extratoRepository.buscar(any(), any(Pageable.class)))
                .thenThrow(new FiltroInvalidoException("Ordenação não suportada"));

        mockMvc.perform(get(ROTA).param("sort", "vl_liquidado; DROP TABLE liquidacao,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve aceitar filtros combinados quando período, cedente e moeda são informados")
    void deveAceitarFiltrosCombinadosQuandoPeriodoCedenteEMoedaSaoInformados() throws Exception {
        when(extratoRepository.buscar(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(ROTA)
                        .param("dataInicio", "2026-07-01")
                        .param("dataFim", "2026-07-31")
                        .param("cedenteId", "1")
                        .param("sgMoeda", "USD"))
                .andExpect(status().isOk());
    }

    private static ExtratoItemRS item() {
        return new ExtratoItemRS(
                1L,
                "11111111-2222-3333-4444-555555555555",
                LocalDateTime.of(2026, 7, 25, 10, 52, 3),
                1L,
                "ACME LTDA",
                "11222333000181",
                "DUP-001",
                "DUPLICATA_MERCANTIL",
                LocalDate.of(2026, 10, 20),
                new BigDecimal("10000.0000"),
                "BRL",
                new BigDecimal("9308.9520"),
                new BigDecimal("0.015000"),
                new BigDecimal("0.010000"),
                87,
                new BigDecimal("9308.9520"),
                new BigDecimal("1.000000"),
                "BRL");
    }
}
