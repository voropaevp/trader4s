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


CREATE TYPE ComboLeg (
    cont_id int,
    ratio int,
    action varchar,
    exchange varchar,
    open_close int,
    short_sale_slot int,
    designated_location varchar,
    exempt_code int
    );

CREATE TYPE DeltaNeutralContract (
    cont_id int,
    delta double,
    price double
    );

CREATE TABLE IF NOT EXISTS contract_by_exc_sym_type
(
    cont_id  int,
    exchange varchar,
    symbol   varchar,
    sec_type varchar,
    PRIMARY KEY ( (exchange, symbol, sec_type) )
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
    combo_legs                        list<frozen<ComboLeg>>,
    delta_neutral_contract            DeltaNeutralContract,
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
    sec_id_list                       list<tuple<text, text>>,
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
