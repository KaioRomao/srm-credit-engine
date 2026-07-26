package br.com.srm.credit.engine.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.srm.credit.engine.dto.rs.CambioRS;
import br.com.srm.credit.engine.exception.CambioException;

@DisplayName("CambioClient — integração com a API Frankfurter")
class CambioClientTest {

    private static final LocalDate DATA = LocalDate.of(2026, 7, 24);

    private MockRestServiceServer servidor;
    private CambioClient cambioClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.frankfurter.dev");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cambioClient = new CambioClient(builder.build());
    }

    @Test
    @DisplayName("deve devolver a cotação quando a API responde um array com um elemento")
    void deveDevolverCotacaoQuandoApiRespondeArrayComUmElemento() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(withSuccess(
                        """
                        [{"date":"2026-07-24","base":"BRL","quote":"USD","rate":0.196980}]
                        """,
                        MediaType.APPLICATION_JSON));

        CambioRS cotacao = cambioClient.consultarCambio(DATA, "BRL", "USD");

        assertThat(cotacao.base()).isEqualTo("BRL");
        assertThat(cotacao.quote()).isEqualTo("USD");
        assertThat(cotacao.rate()).isEqualByComparingTo("0.196980");
        assertThat(cotacao.date()).isEqualTo(DATA);
        servidor.verify();
    }

    @Test
    @DisplayName("deve enviar base, quotes e date como parâmetros de consulta")
    void deveEnviarBaseQuotesEDateComoParametrosDeConsulta() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andExpect(queryParam("base", "BRL"))
                .andExpect(queryParam("quotes", "USD"))
                .andExpect(queryParam("date", "2026-07-24"))
                .andRespond(withSuccess(
                        """
                        [{"date":"2026-07-24","base":"BRL","quote":"USD","rate":0.196980}]
                        """,
                        MediaType.APPLICATION_JSON));

        cambioClient.consultarCambio(DATA, "BRL", "USD");

        servidor.verify();
    }

    @Test
    @DisplayName("deve lançar CambioException quando a API devolve array vazio")
    void deveLancarCambioExceptionQuandoApiDevolveArrayVazio() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cambioClient.consultarCambio(DATA, "BRL", "JPY"))
                .isInstanceOf(CambioException.class)
                .hasMessageContaining("BRL -> JPY");
    }

    @Test
    @DisplayName("deve lançar CambioException quando a API devolve corpo nulo")
    void deveLancarCambioExceptionQuandoApiDevolveCorpoNulo() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cambioClient.consultarCambio(DATA, "BRL", "USD"))
                .isInstanceOf(CambioException.class);
    }

    @Test
    @DisplayName("deve propagar erro HTTP quando a API externa está indisponível")
    void devePropagarErroHttpQuandoApiExternaEstaIndisponivel() {
        servidor.expect(requestTo(containsString("/v2/rates"))).andRespond(withServerError());

        assertThatThrownBy(() -> cambioClient.consultarCambio(DATA, "BRL", "USD"))
                .as("indisponibilidade externa não é ErroDeNegocio: o consumidor deve retentar")
                .isNotInstanceOf(CambioException.class);
    }

    @Test
    @DisplayName("deve ignorar campos desconhecidos quando a API adiciona novos atributos")
    void deveIgnorarCamposDesconhecidosQuandoApiAdicionaNovosAtributos() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(withSuccess(
                        """
                        [{"date":"2026-07-24","base":"BRL","quote":"USD","rate":0.196980,
                          "campoNovoDaApi":"algo","outro":123}]
                        """,
                        MediaType.APPLICATION_JSON));

        CambioRS cotacao = cambioClient.consultarCambio(DATA, "BRL", "USD");

        assertThat(cotacao.rate()).isEqualByComparingTo("0.196980");
    }

    @Test
    @DisplayName("deve usar o primeiro elemento quando a API devolve múltiplas cotações")
    void deveUsarPrimeiroElementoQuandoApiDevolveMultiplasCotacoes() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(withSuccess(
                        """
                        [{"date":"2026-07-24","base":"BRL","quote":"USD","rate":0.196980},
                         {"date":"2026-07-23","base":"BRL","quote":"USD","rate":0.190000}]
                        """,
                        MediaType.APPLICATION_JSON));

        CambioRS cotacao = cambioClient.consultarCambio(DATA, "BRL", "USD");

        assertThat(cotacao.rate()).isEqualByComparingTo("0.196980");
    }

    @Test
    @DisplayName("deve tratar resposta 404 da API sem devolver cotação")
    void deveTratarResposta404DaApiSemDevolverCotacao() {
        servidor.expect(requestTo(containsString("/v2/rates")))
                .andRespond(
                        withSuccess().contentType(MediaType.APPLICATION_JSON).body("[]"));

        assertThatThrownBy(() -> cambioClient.consultarCambio(DATA, "XXX", "YYY"))
                .isInstanceOf(CambioException.class);
        assertThat(HttpStatus.NOT_FOUND.isError()).isTrue();
    }
}
