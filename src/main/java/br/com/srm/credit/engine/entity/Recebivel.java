package br.com.srm.credit.engine.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recebivel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Recebivel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nr_titulo")
    private String nrTitulo;

    @Column(name = "vl_face", precision = 19, scale = 4)
    @NotNull(message = "vl_face obrigatório")
    private BigDecimal vlFace;

    @Column(name = "dt_vencimento", nullable = false)
    @NotNull(message = "dt_vencimento obrigatória")
    private LocalDate dtVencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recebivel_tipo_id")
    @NotNull(message = "recebivel_tipo obrigatório")
    private RecebivelTipo recebivelTipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cedente_id")
    @NotNull(message = "cedente obrigatório")
    private Cedente cedente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moeda_id")
    @NotNull(message = "moeda obrigatória")
    private Moeda moeda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    @NotNull(message = "lote obrigatório")
    private Lote lote;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    public long getQtPrazoDias() {
        return ChronoUnit.DAYS.between(this.getDtCriacao().toLocalDate(), this.getDtVencimento());
    }
}
