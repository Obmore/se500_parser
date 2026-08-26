-- Lookup tables first, then the fat measurement table.
-- Names and limits are stored once per product/feature, not on every reading.

CREATE TABLE machine (
    id        smallserial PRIMARY KEY,
    system_id varchar(64) NOT NULL UNIQUE
);

CREATE TABLE product (
    id   serial PRIMARY KEY,
    name varchar(128) NOT NULL UNIQUE
);

CREATE TABLE feature_def (
    id            serial PRIMARY KEY,
    product_id    int NOT NULL REFERENCES product (id),
    image_name    varchar(64) NOT NULL,
    location_name varchar(64) NOT NULL,
    feature_name  varchar(64) NOT NULL,
    h_up_fail     real,
    h_up_warn     real,
    h_target      real,
    h_low_warn    real,
    h_low_fail    real,
    a_up_fail     real,
    a_up_warn     real,
    a_target      real,
    a_low_warn    real,
    a_low_fail    real,
    v_up_fail     real,
    v_up_warn     real,
    v_target      real,
    v_low_warn    real,
    v_low_fail    real,
    UNIQUE (product_id, image_name, location_name, feature_name)
);

CREATE TABLE inspection (
    id          bigserial PRIMARY KEY,
    machine_id  smallint NOT NULL REFERENCES machine (id),
    product_id  int NOT NULL REFERENCES product (id),
    serial_code varchar(64) NOT NULL,
    test_time   timestamp NOT NULL,
    status      char(1) NOT NULL,
    source_file varchar(260) NOT NULL UNIQUE
);

-- report queries: serial + time + result
CREATE INDEX inspection_report ON inspection (serial_code, test_time, status);

CREATE TABLE measurement (
    inspection_id  bigint NOT NULL REFERENCES inspection (id) ON DELETE CASCADE,
    feature_def_id int    NOT NULL REFERENCES feature_def (id),
    height         real   NOT NULL,
    area           real   NOT NULL,
    volume         real   NOT NULL,
    PRIMARY KEY (inspection_id, feature_def_id)
);
