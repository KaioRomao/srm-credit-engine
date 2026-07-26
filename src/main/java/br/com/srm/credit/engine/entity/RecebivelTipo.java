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
@Table(name = "recebivel_tipo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecebivelTipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ds_recebivel_tipo")
    @NotBlank(message = "ds_recebivel_tipo obrigatória")
    private String dsRecebivelTipo;
}
