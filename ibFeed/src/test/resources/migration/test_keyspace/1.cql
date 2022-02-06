CREATE TABLE IF NOT EXISTS test
(
    req_id        uuid,
    update_time   timestamp,
    state_id      smallint,
    rows_received int,
    PRIMARY KEY ( req_id, update_time )
) WITH CLUSTERING ORDER BY (update_time ASC);
