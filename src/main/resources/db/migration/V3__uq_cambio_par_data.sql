DELETE duplicada
FROM cambio duplicada
JOIN cambio mais_recente
  ON mais_recente.moeda_origem_id  = duplicada.moeda_origem_id
 AND mais_recente.moeda_destino_id = duplicada.moeda_destino_id
 AND mais_recente.dt_fechamento    = duplicada.dt_fechamento
 AND mais_recente.id               > duplicada.id;

ALTER TABLE cambio
    ADD CONSTRAINT uq_cambio_par_data UNIQUE (moeda_origem_id, moeda_destino_id, dt_fechamento);
