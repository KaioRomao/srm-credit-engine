package br.com.srm.credit.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.srm.credit.engine.dto.rq.LoteRQ;
import br.com.srm.credit.engine.dto.rq.LoteRQ.RecebivelItemRQ;
import br.com.srm.credit.engine.dto.rs.LoteRS;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.exception.CambioException;
import br.com.srm.credit.engine.exception.PrecificacaoException;
import br.com.srm.credit.engine.mapper.LoteMapper;
import br.com.srm.credit.engine.repository.CedenteRepository;
import br.com.srm.credit.engine.repository.LoteRepository;
import br.com.srm.credit.engine.repository.MoedaRepository;
import br.com.srm.credit.engine.repository.RecebivelRepository;
import br.com.srm.credit.engine.repository.RecebivelTipoRepository;
import br.com.srm.credit.engine.service.PrecificacaoService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoteServiceImpl")
class LoteServiceImplTest {

    private static final String CNPJ = "11222333000181";
    private static final String DUPLICATA = "DUPLICATA_MERCANTIL";

    @Mock
    private CedenteRepository cedenteRepository;

    @Mock
    private LoteRepository loteRepository;

    @Mock
    private RecebivelRepository recebivelRepository;

    @Mock
    private RecebivelTipoRepository recebivelTipoRepository;

    @Mock
    private MoedaRepository moedaRepository;

    @Mock
    private PrecificacaoService precificacaoService;

    private LoteServiceImpl loteService;

    @BeforeEach
    void setUp() {
        loteService = new LoteServiceImpl(
                cedenteRepository,
                loteRepository,
                recebivelRepository,
                recebivelTipoRepository,
                moedaRepository,
                precificacaoService,
                new LoteMapper());
    }

    @Test
    @DisplayName("deve reaproveitar o cedente existente quando o documento já está cadastrado")
    void deveReaproveitarCedenteExistenteQuandoDocumentoJaEstaCadastrado() {
        Cedente existente = cedente(7L, "ACME ANTIGA");
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(existente));
        prepararDependenciasDeRecebivel();

        LoteRS resposta = loteService.criar(requisicaoComUmItem("ACME NOVA"));

        assertThat(resposta.cedenteNome())
                .as("resolução é por documento; o nome do payload não sobrescreve o cadastro")
                .isEqualTo("ACME ANTIGA");
        verify(cedenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve criar o cedente quando o documento ainda não existe")
    void deveCriarCedenteQuandoDocumentoAindaNaoExiste() {
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.empty());
        when(cedenteRepository.save(any(Cedente.class))).thenAnswer(invocation -> {
            Cedente novo = invocation.getArgument(0);
            novo.setId(1L);
            return novo;
        });
        prepararDependenciasDeRecebivel();

        LoteRS resposta = loteService.criar(requisicaoComUmItem("ACME LTDA"));

        assertThat(resposta.cedenteNome()).isEqualTo("ACME LTDA");
        assertThat(resposta.cedenteDocumento()).isEqualTo(CNPJ);
        verify(cedenteRepository).save(any(Cedente.class));
    }

    @Test
    @DisplayName("deve precificar cada recebível do lote quando há vários itens")
    void devePrecificarCadaRecebivelDoLoteQuandoHaVariosItens() {
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(cedente(1L, "ACME")));
        prepararDependenciasDeRecebivel();

        LoteRQ requisicao = new LoteRQ(
                "Lote 3 itens", CNPJ, "ACME", new BigDecimal("0.01"), List.of(item("T1"), item("T2"), item("T3")));

        LoteRS resposta = loteService.criar(requisicao);

        assertThat(resposta.itens()).hasSize(3);
        verify(recebivelRepository, times(3)).save(any(Recebivel.class));
        verify(precificacaoService, times(3)).precificar(any(), any(), any());
    }

    @Test
    @DisplayName("deve propagar PrecificacaoException quando o tipo de recebível não existe")
    void devePropagarExcecaoQuandoTipoDeRecebivelNaoExiste() {
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(cedente(1L, "ACME")));
        when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(moeda("BRL")));
        when(recebivelTipoRepository.findByDsRecebivelTipo("NOTA_PROMISSORIA")).thenReturn(Optional.empty());

        LoteRQ requisicao = new LoteRQ(
                "Lote inválido",
                CNPJ,
                "ACME",
                new BigDecimal("0.01"),
                List.of(new RecebivelItemRQ(
                        "T1",
                        new BigDecimal("1000.00"),
                        LocalDate.now().plusDays(60),
                        "NOTA_PROMISSORIA",
                        "BRL",
                        "BRL")));

        assertThatThrownBy(() -> loteService.criar(requisicao))
                .isInstanceOf(PrecificacaoException.class)
                .hasMessageContaining("NOTA_PROMISSORIA");

        verify(precificacaoService, never()).precificar(any(), any(), any());
    }

    @Test
    @DisplayName("deve propagar CambioException quando a moeda do recebível não existe")
    void devePropagarExcecaoQuandoMoedaDoRecebivelNaoExiste() {
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(cedente(1L, "ACME")));
        when(moedaRepository.findBySgMoeda("XYZ")).thenReturn(Optional.empty());

        LoteRQ requisicao = new LoteRQ(
                "Lote inválido",
                CNPJ,
                "ACME",
                new BigDecimal("0.01"),
                List.of(new RecebivelItemRQ(
                        "T1", new BigDecimal("1000.00"), LocalDate.now().plusDays(60), DUPLICATA, "XYZ", "XYZ")));

        assertThatThrownBy(() -> loteService.criar(requisicao))
                .isInstanceOf(CambioException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    @DisplayName("deve interromper no primeiro item inválido quando o segundo item falha")
    void deveInterromperNoPrimeiroItemInvalidoQuandoSegundoItemFalha() {
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(cedente(1L, "ACME")));
        when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(moeda("BRL")));
        when(recebivelTipoRepository.findByDsRecebivelTipo(DUPLICATA))
                .thenReturn(Optional.of(new RecebivelTipo(1L, DUPLICATA)));
        when(recebivelTipoRepository.findByDsRecebivelTipo("INVALIDO")).thenReturn(Optional.empty());
        when(recebivelRepository.save(any(Recebivel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(precificacaoService.precificar(any(), any(), any())).thenAnswer(invocation -> precificacao());

        LoteRQ requisicao = new LoteRQ(
                "Lote misto",
                CNPJ,
                "ACME",
                new BigDecimal("0.01"),
                List.of(
                        item("OK"),
                        new RecebivelItemRQ(
                                "RUIM",
                                new BigDecimal("1000.00"),
                                LocalDate.now().plusDays(60),
                                "INVALIDO",
                                "BRL",
                                "BRL")));

        assertThatThrownBy(() -> loteService.criar(requisicao)).isInstanceOf(PrecificacaoException.class);

        verify(precificacaoService, times(1)).precificar(any(), any(), any());
    }

    @Test
    @DisplayName("deve repassar a taxa base do lote para cada precificação")
    void deveRepassarTaxaBaseDoLoteParaCadaPrecificacao() {
        BigDecimal taxaBase = new BigDecimal("0.0275");
        when(cedenteRepository.findByNrDocumento(CNPJ)).thenReturn(Optional.of(cedente(1L, "ACME")));
        prepararDependenciasDeRecebivel();

        LoteRQ requisicao = new LoteRQ("Lote taxa", CNPJ, "ACME", taxaBase, List.of(item("T1")));

        loteService.criar(requisicao);

        verify(precificacaoService).precificar(any(), org.mockito.ArgumentMatchers.eq(taxaBase), any());
    }

    private void prepararDependenciasDeRecebivel() {
        when(moedaRepository.findBySgMoeda("BRL")).thenReturn(Optional.of(moeda("BRL")));
        when(recebivelTipoRepository.findByDsRecebivelTipo(DUPLICATA))
                .thenReturn(Optional.of(new RecebivelTipo(1L, DUPLICATA)));
        when(loteRepository.save(any(Lote.class))).thenAnswer(invocation -> {
            Lote lote = invocation.getArgument(0);
            lote.setId(1L);
            return lote;
        });
        when(recebivelRepository.save(any(Recebivel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(precificacaoService.precificar(any(), any(), any())).thenAnswer(invocation -> precificacao());
    }

    private static LoteRQ requisicaoComUmItem(String nomeCedente) {
        return new LoteRQ("Lote teste", CNPJ, nomeCedente, new BigDecimal("0.01"), List.of(item("T1")));
    }

    private static RecebivelItemRQ item(String nrTitulo) {
        return new RecebivelItemRQ(
                nrTitulo, new BigDecimal("10000.00"), LocalDate.now().plusDays(87), DUPLICATA, "BRL", "BRL");
    }

    private static Cedente cedente(Long id, String nome) {
        Cedente cedente = new Cedente();
        cedente.setId(id);
        cedente.setNmCedente(nome);
        cedente.setNrDocumento(CNPJ);
        return cedente;
    }

    private static Moeda moeda(String sigla) {
        Moeda moeda = new Moeda();
        moeda.setId(1L);
        moeda.setSgMoeda(sigla);
        return moeda;
    }

    private static Precificacao precificacao() {
        Recebivel recebivel = new Recebivel();
        recebivel.setId(1L);
        recebivel.setNrTitulo("T1");
        recebivel.setVlFace(new BigDecimal("10000.00"));
        recebivel.setMoeda(moeda("BRL"));
        recebivel.setDtCriacao(LocalDateTime.now());

        Precificacao precificacao = new Precificacao();
        precificacao.setId(1L);
        precificacao.setRecebivel(recebivel);
        precificacao.setVlLiquido(new BigDecimal("9308.9520"));
        precificacao.setVlConvertido(new BigDecimal("9308.9520"));
        precificacao.setQtPrazoDia(87);
        return precificacao;
    }
}
