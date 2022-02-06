
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

CREATE TABLE IF NOT EXISTS contract
(
    cont_id                           int,
    exchange                          varchar,
    symbol                            varchar,
    currency                          varchar,
    sec_type                          varchar,
    last_trade_date_or_contract_month varchar,
    strike                            varchar,
    multiplier                        varchar,
    primary_exch                      varchar,
    local_symbol                      varchar,
    trading_class                     varchar,
    sec_id_type                       varchar,
    sec_id                            varchar,
    delta_price                       double,
    delta                             double,
    include_expired                   boolean,
    primary key ( cont_id )
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

CREATE TABLE IF NOT EXISTS contract
(
    cont_id                           int,
    symbol                            varchar,
    sec_type                          varchar,
    last_trade_date_or_contract_month varchar,
    strike                            double,
    right                             varchar,
    multiplier                        varchar,
    exchange                          varchar,
    currency                          varchar,
    local_symbol                      varchar,
    primary_exch                      varchar,
    include_expired                   boolean,
    trading_class                     varchar,
    sec_id_type                       varchar,
    sec_id                            varchar,
    combo_legs_description            varchar,
    combo_legs                        list<ComboLeg>,
    delta_neutral_contract            DeltaNeutralContract,
    market_name                       varchar,
    min_tick                          double,
    price_magnifier                   int,
    order_types                       varchar,
    valid_exchanges                   varchar,
    under_con_id                      int,
    long_name                         varchar,
    contract_month                    varchar,
    industry                          varchar,
    category                          varchar,
    subcategory                       varchar,
    time_zone_id                      varchar,
    trading_hours                     varchar,
    liquid_hours                      varchar,
    ev_rule                           varchar,
    ev_multiplier                     double,
    agg_group                         int,
    sec_id_list                       list<tuple<varchar, varchar>>,
    under_symbol                      varchar,
    under_sec_type                    varchar,
    market_rule_ids                   varchar,
    real_expiration_date              varchar,
    last_trade_time                   varchar,
    stock_type                        varchar,
    cusip                             varchar,
    ratings                           varchar,
    desc_append                       varchar,
    bond_type                         varchar,
    coupon_type                       varchar,
    callable                          boolean,
    putable                           boolean,
    coupon                            double,
    convertible                       boolean,
    maturity                          varchar,
    issue_date                        varchar,
    next_option_date                  varchar,
    next_option_type                  varchar,
    next_option_partial               boolean,
    notes                             varchar,
    min_size                          decimal,
    size_increment                    decimal,
    suggested_size_increment          decimal,
    create_time                       timestamp,
    primary key ( cont_id )
);

CREATE TABLE IF NOT EXISTS contract_by_exc_sym_type
(
    cont_id  int,
    exchange varchar,
    symbol   varchar,
    sec_type varchar,
    PRIMARY KEY ( (exchange, symbol, sec_type) )
)
