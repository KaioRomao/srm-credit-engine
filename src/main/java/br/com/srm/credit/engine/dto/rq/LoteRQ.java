package br.com.srm.credit.engine.dto.rq;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LoteRQ(
        @NotBlank(message = "dsReferencia obrigatória") String dsReferencia,
        @NotBlank(message = "cedenteDocumento obrigatório") String cedenteDocumento,
        String cedenteNome,
        @NotNull(message = "vlTaxaBase obrigatória") BigDecimal vlTaxaBase,
        @NotEmpty(message = "informe ao menos um recebível") @Valid List<RecebivelItemRQ> recebiveis) {

    public record RecebivelItemRQ(
            String nrTitulo,
            @NotNull(message = "vlFace obrigatório") @Positive(message = "vlFace deve ser positivo") BigDecimal vlFace,
            @NotNull(message = "dtVencimento obrigatória") @Future(message = "dtVencimento deve ser futura")
                    LocalDate dtVencimento,
            @NotBlank(message = "tipoRecebivel obrigatório") String tipoRecebivel,
            @NotBlank(message = "sgMoeda obrigatória") String sgMoeda,
            String sgMoedaPagamento) {}
}
