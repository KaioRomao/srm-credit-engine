CREATE TABLE moeda (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    sg_moeda    CHAR(3)      NOT NULL,
    ds_moeda    VARCHAR(100) NOT NULL,
    ds_simbolo  VARCHAR(10)  NOT NULL,
    CONSTRAINT pk_moeda PRIMARY KEY (id),
    CONSTRAINT uq_moeda_sg_moeda UNIQUE (sg_moeda)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cedente (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nm_cedente    VARCHAR(150) NOT NULL,
    nr_documento  VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_cedente PRIMARY KEY (id),
    CONSTRAINT uq_cedente_documento UNIQUE (nr_documento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lote (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ds_referencia  VARCHAR(100) NOT NULL,
    dt_criacao     DATETIME     NOT NULL,
    CONSTRAINT pk_lote PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE recebivel_tipo (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    ds_recebivel_tipo   VARCHAR(100)  NOT NULL,
    CONSTRAINT pk_recebivel_tipo PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cambio (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    vl_cambio        DECIMAL(19,6) NOT NULL,
    moeda_origem_id  BIGINT        NOT NULL,
    moeda_destino_id BIGINT        NOT NULL,
    dt_fechamento    DATETIME      NOT NULL,
    CONSTRAINT pk_cambio PRIMARY KEY (id),
    CONSTRAINT fk_cambio_moeda_origem  FOREIGN KEY (moeda_origem_id)  REFERENCES moeda (id),
    CONSTRAINT fk_cambio_moeda_destino FOREIGN KEY (moeda_destino_id) REFERENCES moeda (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cambio_cotacao ON cambio (moeda_origem_id, moeda_destino_id, dt_fechamento DESC);
CREATE INDEX idx_cambio_moeda_destino ON cambio (moeda_destino_id);

CREATE TABLE recebivel (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    nr_titulo          VARCHAR(50)   NULL,
    vl_face            DECIMAL(19,4) NOT NULL,
    dt_vencimento      DATE          NOT NULL,
    recebivel_tipo_id  BIGINT        NOT NULL,
    cedente_id         BIGINT        NOT NULL,
    moeda_id           BIGINT        NOT NULL,
    lote_id            BIGINT        NOT NULL,
    dt_criacao         DATETIME      NOT NULL,
    CONSTRAINT pk_recebivel PRIMARY KEY (id),
    CONSTRAINT fk_recebivel_tipo    FOREIGN KEY (recebivel_tipo_id) REFERENCES recebivel_tipo (id),
    CONSTRAINT fk_recebivel_cedente FOREIGN KEY (cedente_id)        REFERENCES cedente (id),
    CONSTRAINT fk_recebivel_moeda   FOREIGN KEY (moeda_id)          REFERENCES moeda (id),
    CONSTRAINT fk_recebivel_lote    FOREIGN KEY (lote_id)           REFERENCES lote (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_recebivel_cedente    ON recebivel (cedente_id);
CREATE INDEX idx_recebivel_lote       ON recebivel (lote_id);
CREATE INDEX idx_recebivel_tipo       ON recebivel (recebivel_tipo_id);
CREATE INDEX idx_recebivel_moeda      ON recebivel (moeda_id);
CREATE INDEX idx_recebivel_vencimento ON recebivel (dt_vencimento);

CREATE TABLE precificacao (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    recebivel_id     BIGINT        NOT NULL,
    vl_liquido       DECIMAL(19,4) NULL,
    vl_convertido    DECIMAL(19,4) NULL,
    qt_prazo_dia     INT           NULL,
    vl_spread        DECIMAL(19,6) NULL,
    vl_taxa_base     DECIMAL(19,6) NULL,
    moeda_destino_id BIGINT        NULL,
    cambio_id        BIGINT        NULL,
    dt_criacao       DATETIME      NOT NULL,
    CONSTRAINT pk_precificacao PRIMARY KEY (id),
    CONSTRAINT fk_precificacao_recebivel     FOREIGN KEY (recebivel_id)     REFERENCES recebivel (id),
    CONSTRAINT fk_precificacao_moeda_destino FOREIGN KEY (moeda_destino_id) REFERENCES moeda (id),
    CONSTRAINT fk_precificacao_cambio        FOREIGN KEY (cambio_id)        REFERENCES cambio (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_precificacao_recebivel     ON precificacao (recebivel_id);
CREATE INDEX idx_precificacao_dt_criacao    ON precificacao (dt_criacao);
CREATE INDEX idx_precificacao_moeda_destino ON precificacao (moeda_destino_id);
CREATE INDEX idx_precificacao_cambio        ON precificacao (cambio_id);

CREATE TABLE liquidacao (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    version             BIGINT        NOT NULL DEFAULT 0,
    track_id            VARCHAR(36)   NOT NULL,
    precificacao_id     BIGINT        NOT NULL,
    cedente_id          BIGINT        NOT NULL,
    moeda_liquidacao_id BIGINT        NOT NULL,
    cambio_id           BIGINT        NULL,
    vl_liquidado        DECIMAL(19,4) NULL,
    vl_cambio_aplicado  DECIMAL(19,6) NULL,
    status              VARCHAR(20)   NOT NULL,
    ds_observacao       VARCHAR(500)  NULL,
    dt_criacao          DATETIME      NOT NULL,
    dt_liquidacao       DATETIME      NULL,
    dt_atualizacao      DATETIME      NULL,
    CONSTRAINT pk_liquidacao PRIMARY KEY (id),
    CONSTRAINT uq_liquidacao_track_id     UNIQUE (track_id),
    CONSTRAINT uq_liquidacao_precificacao UNIQUE (precificacao_id),
    CONSTRAINT chk_liquidacao_status CHECK (status IN ('PENDENTE','PROCESSANDO','LIQUIDADA','FALHA','CANCELADA')),
    CONSTRAINT fk_liquidacao_precificacao FOREIGN KEY (precificacao_id)     REFERENCES precificacao (id),
    CONSTRAINT fk_liquidacao_cedente      FOREIGN KEY (cedente_id)          REFERENCES cedente (id),
    CONSTRAINT fk_liquidacao_moeda        FOREIGN KEY (moeda_liquidacao_id) REFERENCES moeda (id),
    CONSTRAINT fk_liquidacao_cambio       FOREIGN KEY (cambio_id)           REFERENCES cambio (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_liquidacao_cedente_data ON liquidacao (cedente_id, dt_liquidacao);
CREATE INDEX idx_liquidacao_dt_liquidacao ON liquidacao (dt_liquidacao);
CREATE INDEX idx_liquidacao_status ON liquidacao (status);
CREATE INDEX idx_liquidacao_moeda ON liquidacao (moeda_liquidacao_id);
CREATE INDEX idx_liquidacao_cambio ON liquidacao (cambio_id);
