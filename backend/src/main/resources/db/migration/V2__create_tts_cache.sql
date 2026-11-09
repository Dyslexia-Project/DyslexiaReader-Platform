CREATE TABLE tts_cache (
    cache_hash VARCHAR(64) PRIMARY KEY,
    voice_id VARCHAR(64) NOT NULL,
    language VARCHAR(16) NOT NULL,
    speed DOUBLE PRECISION NOT NULL,
    character_count INTEGER NOT NULL,
    audio_mime_type VARCHAR(32) NOT NULL,
    audio_base64 TEXT NOT NULL,
    timing_granularity VARCHAR(32) NOT NULL,
    speech_marks JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tts_cache_expires_at ON tts_cache (expires_at);
