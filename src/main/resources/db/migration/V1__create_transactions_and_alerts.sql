CREATE TABLE transactions (
    txn_id VARCHAR(64) PRIMARY KEY,
    account_number VARCHAR(12) NOT NULL CHECK (CHAR_LENGTH(account_number) = 12),
    txn_time TIMESTAMP WITH TIME ZONE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    channel VARCHAR(8) NOT NULL CHECK (channel IN ('CASH', 'NEFT', 'IMPS', 'UPI', 'RTGS')),
    counterparty_account VARCHAR(64) NOT NULL,
    response_json TEXT
);

CREATE INDEX idx_transactions_account_time ON transactions (account_number, txn_time);
CREATE INDEX idx_transactions_time_account ON transactions (txn_time, account_number);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(12) NOT NULL,
    scenario_code VARCHAR(32) NOT NULL,
    alert_date DATE NOT NULL,
    trigger_txn_id VARCHAR(64) NOT NULL REFERENCES transactions (txn_id),
    trigger_time TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(16) NOT NULL CHECK (source IN ('REALTIME', 'BATCH')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN')),
    scenario_details TEXT NOT NULL,
    CONSTRAINT uq_alert_account_scenario_date
        UNIQUE (account_number, scenario_code, alert_date)
);

CREATE INDEX idx_alerts_trigger_time ON alerts (trigger_time DESC);
CREATE INDEX idx_alerts_scenario_source_date
    ON alerts (scenario_code, source, alert_date DESC);

CREATE TABLE alert_contributing_transactions (
    alert_id UUID NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    txn_id VARCHAR(64) NOT NULL REFERENCES transactions (txn_id),
    PRIMARY KEY (alert_id, position)
);

CREATE INDEX idx_alert_contributing_txn_id ON alert_contributing_transactions (txn_id);
