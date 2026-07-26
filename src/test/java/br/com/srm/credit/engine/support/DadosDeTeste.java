package br.com.srm.credit.engine.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srm.credit.engine.entity.Cambio;
import br.com.srm.credit.engine.entity.Cedente;
import br.com.srm.credit.engine.entity.Liquidacao;
import br.com.srm.credit.engine.entity.Lote;
import br.com.srm.credit.engine.entity.Moeda;
import br.com.srm.credit.engine.entity.Precificacao;
import br.com.srm.credit.engine.entity.Recebivel;
import br.com.srm.credit.engine.entity.RecebivelTipo;
import br.com.srm.credit.engine.enums.StatusLiquidacao;

public final class DadosDeTeste {

    public static final String CNPJ_ACME = "11222333000181";
    public static final String CNPJ_BETA = "98765432000198";

    private DadosDeTeste() {}

    public static Cedente cedente(String nome, String documento) {
        Cedente cedente = new Cedente();
        cedente.setNmCedente(nome);
        cedente.setNrDocumento(documento);
        return cedente;
    }

    public static Lote lote(String referencia) {
        Lote lote = new Lote();
        lote.setDsReferencia(referencia);
        lote.setDtCriacao(LocalDateTime.now());
        return lote;
    }

    public static Recebivel recebivel(
            String nrTitulo, BigDecimal vlFace, Cedente cedente, Lote lote, Moeda moeda, RecebivelTipo tipo) {
        Recebivel recebivel = new Recebivel();
        recebivel.setNrTitulo(nrTitulo);
        recebivel.setVlFace(vlFace);
        recebivel.setDtVencimento(LocalDate.now().plusDays(87));
        recebivel.setRecebivelTipo(tipo);
        recebivel.setCedente(cedente);
        recebivel.setMoeda(moeda);
        recebivel.setLote(lote);
        recebivel.setDtCriacao(LocalDateTime.now());
        return recebivel;
    }

    public static Precificacao precificacao(Recebivel recebivel, BigDecimal vlLiquido, BigDecimal vlSpread) {
        Precificacao precificacao = new Precificacao();
        precificacao.setRecebivel(recebivel);
        precificacao.setVlLiquido(vlLiquido);
        precificacao.setVlConvertido(vlLiquido);
        precificacao.setQtPrazoDia(87);
        precificacao.setVlSpread(vlSpread);
        precificacao.setVlTaxaBase(new BigDecimal("0.010000"));
        precificacao.setDtCriacao(LocalDateTime.now());
        return precificacao;
    }

    public static Liquidacao liquidacao(
            Precificacao precificacao,
            Cedente cedente,
            Moeda moedaLiquidacao,
            StatusLiquidacao status,
            LocalDateTime dtLiquidacao) {
        Liquidacao liquidacao = new Liquidacao();
        liquidacao.setTrackId(UUID.randomUUID());
        liquidacao.setPrecificacao(precificacao);
        liquidacao.setCedente(cedente);
        liquidacao.setMoedaLiquidacao(moedaLiquidacao);
        liquidacao.setStatus(status);
        liquidacao.setDtCriacao(LocalDateTime.now());
        if (status == StatusLiquidacao.LIQUIDADA) {
            liquidacao.setVlLiquidado(precificacao.getVlLiquido());
            liquidacao.setVlCambioAplicado(BigDecimal.ONE);
            liquidacao.setDtLiquidacao(dtLiquidacao);
        }
        return liquidacao;
    }

    public static Cambio cambio(Moeda origem, Moeda destino, BigDecimal taxa, LocalDateTime dtFechamento) {
        Cambio cambio = new Cambio();
        cambio.setMoedaOrigem(origem);
        cambio.setMoedaDestino(destino);
        cambio.setVlCambio(taxa);
        cambio.setDtFechamento(dtFechamento);
        return cambio;
    }
}
