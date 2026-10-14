CREATE TABLE IF NOT EXISTS terminals (
    terminal_id VARCHAR(64) PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial master terminals for fraud detection
INSERT INTO terminals (terminal_id, active, description) VALUES
    ('TERM-001', TRUE, 'Standard POS Terminal 1'),
    ('TERM-002', TRUE, 'Standard POS Terminal 2'),
    ('TERM-HIGH-RISK', TRUE, 'Flagged High Risk Location'),
    ('TERM-SUSPENDED', FALSE, 'Suspended Terminal');
