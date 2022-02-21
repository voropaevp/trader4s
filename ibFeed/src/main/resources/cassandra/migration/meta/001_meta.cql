CREATE TABLE IF NOT EXISTS state
(
    req_id        uuid,
    update_time   timestamp,
    state_id      smallint,
    rows_received int,
    PRIMARY KEY ( req_id, update_time )
) WITH CLUSTERING ORDER BY (update_time ASC);

CREATE TABLE IF NOT EXISTS data_sub
(
    side        ascii,
    contract_id int,
    data_type   ascii,
    create_time timestamp,
    req_id      uuid,
    primary key ( (side, contract_id, data_type), create_time )
) WITH CLUSTERING ORDER BY (create_time asc);

CREATE TABLE IF NOT EXISTS state_description
(
    state_id    smallint,
    name        varchar,
    description varchar,
    primary key (state_id)
);


CREATE TYPE combo_leg (
    cont_id int,
    ratio int,
    action varchar,
    exchange varchar,
    open_close int,
    short_sale_slot int,
    designated_location varchar,
    exempt_code int
    );

CREATE TYPE delta_neutral_contract (
    cont_id int,
    delta double,
    price double
    );

CREATE TYPE seq_id (
    m_tag text,
    m_value text
    );

CREATE TABLE IF NOT EXISTS contract_by_entry_hash
(
    cont_id    int,
    entry_hash int,
    PRIMARY KEY ( entry_hash)
);


CREATE TABLE IF NOT EXISTS contract
(
    cont_id                           int,
    symbol                            text,
    sec_type                          text,
    last_trade_date_or_contract_month text,
    strike                            double,
    right                             text,
    multiplier                        text,
    exchange                          text,
    currency                          text,
    local_symbol                      text,
    primary_exch                      text,
    trading_class                     text,
    include_expired                   boolean,
    sec_id                            text,
    sec_id_type                       text,
    combo_legs_description            text,
    combo_legs                        list<frozen<combo_leg>>,
    delta_neutral_contract            frozen<delta_neutral_contract>,
    market_name                       text,
    min_tick                          double,
    price_magnifier                   int,
    order_types                       text,
    valid_exchanges                   text,
    under_con_id                      int,
    long_name                         text,
    contract_month                    text,
    industry                          text,
    category                          text,
    subcategory                       text,
    time_zone_id                      text,
    trading_hours                     text,
    liquid_hours                      text,
    ev_rule                           text,
    ev_multiplier                     double,
    md_size_multiplier                int,
    agg_group                         int,
    sec_id_list                       list<frozen<seq_id>>,
    under_symbol                      text,
    under_sec_type                    text,
    market_rule_ids                   text,
    real_expiration_date              text,
    last_trade_time                   text,
    stock_type                        text,
    cusip                             text,
    ratings                           text,
    desc_append                       text,
    bond_type                         text,
    coupon_type                       text,
    callable                          boolean,
    putable                           boolean,
    coupon                            double,
    convertible                       boolean,
    maturity                          text,
    issue_date                        text,
    next_option_date                  text,
    next_option_type                  text,
    next_option_partial               boolean,
    notes                             text,
    primary key ( cont_id )
);


CREATE TABLE IF NOT EXISTS request_contract
(
    req_id                            uuid,
    symbol                            text,
    sec_type                          text,
    last_trade_date_or_contract_month text,
    strike                            double,
    right                             text,
    multiplier                        text,
    exchange                          text,
    currency                          text,
    local_symbol                      text,
    primary_exch                      text,
    trading_class                     text,
    include_expired                   boolean,
    sec_id                            text,
    sec_id_type                       text,
    combo_legs_description            text,
    combo_legs                        list<frozen<combo_leg>>,
    delta_neutral_contract            frozen<delta_neutral_contract>,
    state                             text,
    PRIMARY KEY (req_id)
);

CREATE TABLE IF NOT EXISTS request_contract_by_entry_hash
(
    entry_hash int,
    state      text,
    req_id     uuid,
    PRIMARY KEY ((entry_hash, state))
);

CREATE TABLE IF NOT EXISTS request_data
(
    req_id       uuid,
    request_type text,
    size         text,
    cont_id      int,
    data_type    text,
    state        text,
    start_time   timestamp,
    end_time     timestamp,
    PRIMARY KEY (req_id)
);


CREATE TABLE IF NOT EXISTS request_data_by_props
(
    req_id       uuid,
    request_type text,
    size         text,
    cont_id      int,
    data_type    text,
    state        text,
    start_time   timestamp,
    create_time  timestamp,
    PRIMARY KEY ((request_type, size, cont_id, data_type, state), start_time)
);


CREATE TABLE IF NOT EXISTS request_state_audit
(
    req_id        uuid,
    create_time   timestamp,
    state         text,
    rows_received bigint,
    error         text,
    PRIMARY KEY ((req_id, state), create_time)
);

