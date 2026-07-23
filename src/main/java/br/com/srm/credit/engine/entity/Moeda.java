package br.com.srm.credit.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "moeda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Moeda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sg_moeda", length = 3)
    @NotBlank(message = "sg_moeda obrigatória")
    private String sgMoeda;

    @Column(name = "ds_moeda")
    @NotBlank(message = "ds_moeda obrigatória")
    private String dsMoeda;

    @Column(name = "ds_simbolo")
    @NotBlank(message = "ds_simbolo obrigatório")
    private String dsSimbolo;

}