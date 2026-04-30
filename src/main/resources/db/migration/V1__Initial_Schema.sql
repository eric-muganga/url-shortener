-- Create sequence for distributed ID generation
CREATE SEQUENCE url_id_sequence
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

-- Create URLs table
CREATE TABLE urls (
                      id BIGINT PRIMARY KEY DEFAULT nextval('url_id_sequence'),
                      short_code VARCHAR(12) NOT NULL UNIQUE,
                      original_url TEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      expires_at TIMESTAMP,
                      is_deleted BOOLEAN DEFAULT FALSE
);

-- Indexes
CREATE INDEX idx_short_code ON urls(short_code);
CREATE INDEX idx_created_at ON urls(created_at);
CREATE INDEX idx_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;

-- Metrics/Statistics table
CREATE TABLE url_access_logs (
                                 id BIGSERIAL PRIMARY KEY,
                                 short_code VARCHAR(12) NOT NULL,
                                 accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 http_status INT,
                                 user_agent TEXT,
                                 FOREIGN KEY (short_code) REFERENCES urls(short_code)
);

CREATE INDEX idx_access_logs_short_code ON url_access_logs(short_code, accessed_at DESC);