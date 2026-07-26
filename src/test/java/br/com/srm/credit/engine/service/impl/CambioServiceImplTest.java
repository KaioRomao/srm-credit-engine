package br.com.srm.credit.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.srm.credit.engine.client.CambioClient;
import br.com.srm.credit.engine.dto.rs.CambioRS;
import br.com.srm.credit.engine.entity.Cambio;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.repository.CambioRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CambioServiceImpl")
class CambioServiceImplTest {

    private static final LocalDate DATA_FECHAMENTO = LocalDate.of(2026, 7, 24);
    private static final BigDecimal TAXA_BRL_USD = new BigDecimal("0.196980");

    @Mock
    private CambioClient cambioClient;

    @Mock
    private CambioRepository cambioRepository;

    @Mock
    private MoedaRepository moedaRepository;

    @InjectMocks
    private CambioServiceImpl cambioService;

    private Moeda brl;
    private Moeda usd;

    @BeforeEach
    void setUp() {
        brl = moeda(1L, "BRL");
        usd = moeda(2L, "USD");
    }

    @Nested
    @DisplayName("sincronizar")
    class Sincronizar {

        @Test
        @DisplayName("deve criar cotação quando o par e a data ainda não existem")
        void deveCriarCotacaoQuandoParEDataNaoExistem() {
            when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(brl));
            when(moedaRepository.findBySgMoeda("USD")).thenReturn(Optional.of(usd));
            when(cambioClient.consultarCambio(DATA_FECHAMENTO, "BRL", "USD"))
                    .thenReturn(new CambioRS(DATA_FECHAMENTO, "BRL", "USD", TAXA_BRL_USD));
            when(cambioRepository.buscarCambioDoFechamento("BRL", "USD", DATA_FECHAMENTO.atStartOfDay()))
                    .thenReturn(Optional.empty());
            when(cambioRepository.save(any(Cambio.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Cambio resultado = cambioService.sincronizar(DATA_FECHAMENTO, "BRL", "USD");

            assertThat(resultado.getVlCambio()).isEqualByComparingTo(TAXA_BRL_USD);
            assertThat(resultado.getMoedaOrigem()).isEqualTo(brl);
            assertThat(resultado.getMoedaDestino()).isEqualTo(usd);
            assertThat(resultado.getDtFechamento()).isEqualTo(DATA_FECHAMENTO.atStartOfDay());
        }

        @Test
        @DisplayName("deve atualizar a cotação existente em vez de duplicar quando o par e a data já existem")
        void deveAtualizarCotacaoExistenteQuandoParEDataJaExistem() {
            Cambio existente = new Cambio();
            existente.setId(99L);
            existente.setVlCambio(new BigDecimal("0.100000"));

            when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(brl));
            when(moedaRepository.findBySgMoeda("USD")).thenReturn(Optional.of(usd));
            when(cambioClient.consultarCambio(DATA_FECHAMENTO, "BRL", "USD"))
                    .thenReturn(new CambioRS(DATA_FECHAMENTO, "BRL", "USD", TAXA_BRL_USD));
            when(cambioRepository.buscarCambioDoFechamento("BRL", "USD", DATA_FECHAMENTO.atStartOfDay()))
                    .thenReturn(Optional.of(existente));
            when(cambioRepository.save(any(Cambio.class))).thenAnswer(invocation -> invocation.getArgument(0));

            cambioService.sincronizar(DATA_FECHAMENTO, "BRL", "USD");

            ArgumentCaptor<Cambio> captor = ArgumentCaptor.forClass(Cambio.class);
            verify(cambioRepository).save(captor.capture());
            assertThat(captor.getValue().getId())
                    .as("upsert: reaproveita o registro existente, não cria outro")
                    .isEqualTo(99L);
            assertThat(captor.getValue().getVlCambio()).isEqualByComparingTo(TAXA_BRL_USD);
        }

        @Test
        @DisplayName("deve lançar CambioException quando a moeda de origem não existe")
        void deveLancarExcecaoQuandoMoedaDeOrigemNaoExiste() {
            when(moedaRepository.findBySgMoeda("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cambioService.sincronizar(DATA_FECHAMENTO, "XYZ", "USD"))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("Moeda não encontrada: XYZ");

            verify(cambioClient, never()).consultarCambio(any(), any(), any());
        }

        @Test
        @DisplayName("deve lançar CambioException quando a moeda de destino não existe")
        void deveLancarExcecaoQuandoMoedaDeDestinoNaoExiste() {
            when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(brl));
            when(moedaRepository.findBySgMoeda("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cambioService.sincronizar(DATA_FECHAMENTO, "BRL", "XYZ"))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("Moeda não encontrada: XYZ");
        }

        @Test
        @DisplayName("deve usar a data devolvida pela API e não a data solicitada")
        void deveUsarDataDevolvidaPelaApiENaoADataSolicitada() {
            LocalDate dataRetornada = LocalDate.of(2026, 7, 23);
            when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(brl));
            when(moedaRepository.findBySgMoeda("USD")).thenReturn(Optional.of(usd));
            when(cambioClient.consultarCambio(DATA_FECHAMENTO, "BRL", "USD"))
                    .thenReturn(new CambioRS(dataRetornada, "BRL", "USD", TAXA_BRL_USD));
            when(cambioRepository.buscarCambioDoFechamento("BRL", "USD", dataRetornada.atStartOfDay()))
                    .thenReturn(Optional.empty());
            when(cambioRepository.save(any(Cambio.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Cambio resultado = cambioService.sincronizar(DATA_FECHAMENTO, "BRL", "USD");

            assertThat(resultado.getDtFechamento())
                    .as("fim de semana ou feriado devolve a última cotação disponível")
                    .isEqualTo(dataRetornada.atStartOfDay());
        }
    }

    @Nested
    @DisplayName("buscarUltimaCotacao")
    class BuscarUltimaCotacao {

        @Test
        @DisplayName("deve devolver a taxa quando existe cotação para o par")
        void deveDevolverTaxaQuandoExisteCotacaoParaOPar() {
            when(cambioRepository.buscarUltimoCambio("BRL", "USD")).thenReturn(Optional.of(cambio(TAXA_BRL_USD)));

            BigDecimal taxa = cambioService.buscarUltimaCotacao("BRL", "USD");

            assertThat(taxa).isEqualByComparingTo(TAXA_BRL_USD);
        }

        @Test
        @DisplayName("deve lançar CambioException quando não existe cotação para o par")
        void deveLancarExcecaoQuandoNaoExisteCotacaoParaOPar() {
            when(cambioRepository.buscarUltimoCambio("BRL", "JPY")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cambioService.buscarUltimaCotacao("BRL", "JPY"))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("BRL->JPY");
        }
    }

    @Nested
    @DisplayName("converter")
    class Converter {

        @Test
        @DisplayName("deve devolver o mesmo valor quando origem e destino são a mesma moeda")
        void deveDevolverMesmoValorQuandoOrigemEDestinoSaoIguais() {
            BigDecimal valor = new BigDecimal("1234.5678");

            BigDecimal convertido = cambioService.converter(valor, "BRL", "BRL");

            assertThat(convertido).isSameAs(valor);
            verify(cambioRepository, never()).buscarUltimoCambio(any(), any());
        }

        @Test
        @DisplayName("deve multiplicar pela taxa direta quando existe cotação no sentido pedido")
        void deveMultiplicarPelaTaxaDiretaQuandoExisteCotacaoNoSentidoPedido() {
            when(cambioRepository.buscarUltimoCambio("BRL", "USD")).thenReturn(Optional.of(cambio(TAXA_BRL_USD)));

            BigDecimal convertido = cambioService.converter(new BigDecimal("1000.0000"), "BRL", "USD");

            assertThat(convertido).isEqualByComparingTo("196.9800");
        }

        @Test
        @DisplayName("deve aplicar taxa inversa quando só existe cotação no sentido oposto")
        void deveAplicarTaxaInversaQuandoSoExisteCotacaoNoSentidoOposto() {
            when(cambioRepository.buscarUltimoCambio("USD", "BRL")).thenReturn(Optional.empty());
            when(cambioRepository.buscarUltimoCambio("BRL", "USD"))
                    .thenReturn(Optional.of(cambio(new BigDecimal("0.200000"))));

            BigDecimal convertido = cambioService.converter(new BigDecimal("100.0000"), "USD", "BRL");

            assertThat(convertido).as("1 / 0.2 = 5, logo 100 USD = 500 BRL").isEqualByComparingTo("500.0000");
        }

        @Test
        @DisplayName("deve lançar CambioException quando não há cotação em nenhum dos sentidos")
        void deveLancarExcecaoQuandoNaoHaCotacaoEmNenhumSentido() {
            when(cambioRepository.buscarUltimoCambio("BRL", "JPY")).thenReturn(Optional.empty());
            when(cambioRepository.buscarUltimoCambio("JPY", "BRL")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cambioService.converter(BigDecimal.TEN, "BRL", "JPY"))
                    .isInstanceOf(CambioException.class)
                    .hasMessageContaining("BRL->JPY");
        }

        @Test
        @DisplayName("deve arredondar o resultado em quatro casas decimais")
        void deveArredondarResultadoEmQuatroCasasDecimais() {
            when(cambioRepository.buscarUltimoCambio("BRL", "USD"))
                    .thenReturn(Optional.of(cambio(new BigDecimal("0.123456"))));

            BigDecimal convertido = cambioService.converter(new BigDecimal("999.99"), "BRL", "USD");

            assertThat(convertido.scale()).isEqualTo(4);
        }
    }

    private static Moeda moeda(Long id, String sigla) {
        Moeda moeda = new Moeda();
        moeda.setId(id);
        moeda.setSgMoeda(sigla);
        return moeda;
    }

    private static Cambio cambio(BigDecimal taxa) {
        Cambio cambio = new Cambio();
        cambio.setVlCambio(taxa);
        cambio.setDtFechamento(LocalDateTime.of(2026, 7, 24, 0, 0));
        return cambio;
    }
}
