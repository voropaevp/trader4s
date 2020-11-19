CREATE TABLE IF NOT EXISTS historic_data.bar
(
    ts      timestamp,
    date    date,
    size_id int,
    cont_id int,
    open    double,
    high    double,
    low     double,
    close   double,
    volume  double,
    count   int,
    wap     double,
    extra   text,
    PRIMARY KEY ( (size_id, cont_id, date) , ts)
) WITH CLUSTERING ORDER BY (ts DESC);
