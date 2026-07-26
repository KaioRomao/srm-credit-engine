package br.com.srm.credit.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraints.br.CNPJ;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cedente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cedente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nm_cedente")
    @NotBlank(message = "nm_cedente obrigatório")
    private String nmCedente;

    @Column(name = "nr_documento", length = 14, unique = true)
    @NotBlank(message = "nr_documento obrigatório")
    @CNPJ
    private String nrDocumento;
}
