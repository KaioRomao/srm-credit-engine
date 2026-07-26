package br.com.srm.credit.engine.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.srm.credit.engine.enums.StatusLiquidacao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "liquidacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Liquidacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "track_id", length = 36, nullable = false, updatable = false, unique = true)
    @NotNull(message = "track_id obrigatório")
    private UUID trackId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precificacao_id", unique = true)
    @NotNull(message = "precificacao obrigatória")
    private Precificacao precificacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cedente_id")
    @NotNull(message = "cedente obrigatório")
    private Cedente cedente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moeda_liquidacao_id")
    @NotNull(message = "moeda_liquidacao obrigatória")
    private Moeda moedaLiquidacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambio_id")
    private Cambio cambio;

    @Column(name = "vl_liquidado", precision = 19, scale = 4)
    private BigDecimal vlLiquidado;

    @Column(name = "vl_cambio_aplicado", precision = 19, scale = 6)
    private BigDecimal vlCambioAplicado;

    @Enumerated(EnumType.STRING)
    @Column(name = "st_liquidacao", length = 20, nullable = false)
    @NotNull(message = "st_liquidacao obrigatório")
    private StatusLiquidacao status;

    @Column(name = "ds_observacao", length = 500)
    private String dsObservacao;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_liquidacao")
    private LocalDateTime dtLiquidacao;

    @Column(name = "dt_atualizacao")
    private LocalDateTime dtAtualizacao;
}
