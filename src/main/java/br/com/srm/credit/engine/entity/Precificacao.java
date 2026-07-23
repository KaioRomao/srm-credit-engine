package br.com.srm.credit.engine.entity;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "precificacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Precificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recebivel_id")
    @NotNull(message = "recebivel obrigatório")
    private Recebivel recebivel;

    @Column(name = "vl_liquido", precision = 19, scale = 4)
    private BigDecimal vlLiquido;

    @Column(name = "vl_convertido", precision = 19, scale = 4)
    private BigDecimal vlConvertido;

    @Column(name = "qt_prazo_dia")
    private Integer qtPrazoDia;

    @Column(name = "vl_spread", precision = 19, scale = 6)
    private BigDecimal vlSpread;

    @Column(name = "vl_taxa_base", precision = 19, scale = 6)
    private BigDecimal vlTaxaBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moeda_destino_id")
    private Moeda moedaDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambio_id")
    private Cambio cambio;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;
}
