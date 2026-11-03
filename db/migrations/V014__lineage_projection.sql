-- Task 21: AGE lineage projection tables (relational provenance + watermark).
-- The graph itself lives in AGE; these tables hold build provenance and the
-- relational watermark that gates dependent certification on projection freshness.

CREATE TABLE epm_lineage_projection (
    projection_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    graph_name     varchar(64) NOT NULL,
    build_kind     varchar(16) NOT NULL,
    node_count     integer     NOT NULL,
    edge_count     integer     NOT NULL,
    source_watermark timestamptz NOT NULL,
    built_at_utc   timestamptz NOT NULL DEFAULT now(),
    built_by       varchar(128) NOT NULL,
    CONSTRAINT ck_projection_kind CHECK (build_kind IN ('FULL_REBUILD', 'DIFFERENTIAL'))
);

CREATE INDEX ix_projection_graph ON epm_lineage_projection (graph_name, built_at_utc DESC);
