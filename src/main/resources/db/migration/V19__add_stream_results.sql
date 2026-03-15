-- V3: Add stream_results table with partitioning support for 1M+ logs
CREATE TABLE IF NOT EXISTS stream_results (
    id BIGSERIAL,
    proxy_id VARCHAR(255),
    track_id VARCHAR(255) NOT NULL,
    stream_duration INT, -- seconds
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50), -- SUCCESS, FAILED, TIMEOUT
    error_message TEXT,
    PRIMARY KEY (id, completed_at)
) PARTITION BY RANGE (completed_at);

-- Create partition for February 2026
CREATE TABLE IF NOT EXISTS stream_results_2026_02 PARTITION OF stream_results
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- Create indexes on the parent table (will be applied to partitions)
CREATE INDEX IF NOT EXISTS idx_stream_completed_at ON stream_results (completed_at);
CREATE INDEX IF NOT EXISTS idx_stream_status ON stream_results (status);
CREATE INDEX IF NOT EXISTS idx_stream_track_id ON stream_results (track_id);
