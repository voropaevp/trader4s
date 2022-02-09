CREATE TABLE IF NOT EXISTS bar
(
    ts        timestamp,
    size      text,
    cont_id   int,
    data_type text,
    open      double,
    high      double,
    low       double,
    close     double,
    volume    bigint,
    count     int,
    wap       double,
    extra     text,
    PRIMARY KEY ( (cont_id, size, data_type) , ts)
) WITH CLUSTERING ORDER BY (ts DESC);
