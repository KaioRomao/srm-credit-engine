package br.com.srm.credit.engine.dto.rs;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoteRS(
        Long id,
        String dsReferencia,
        String cedenteNome,
        String cedenteDocumento,
        LocalDateTime dtCriacao,
        List<PrecificacaoItemRS> itens) {

    public record PrecificacaoItemRS(
            Long precificacaoId,
            Long recebivelId,
            String nrTitulo,
            BigDecimal vlFace,
            BigDecimal vlLiquido,
            BigDecimal vlConvertido,
            Integer qtPrazoDia,
            String sgMoeda,
            String sgMoedaPagamento) {}
}
