package br.com.srm.credit.engine.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "cambio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "vl_cambio", precision = 19, scale = 6)
    @NotNull(message = "vl_cambio do câmbio obrigatório")
    private BigDecimal vlCambio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moeda_origem_id")
    private Moeda moedaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moeda_destino_id")
    private Moeda moedaDestino;

    @Column(name = "dt_fechamento")
    @NotNull(message = "dt_fechamento obrigatória")
    private LocalDateTime dtFechamento;
}
