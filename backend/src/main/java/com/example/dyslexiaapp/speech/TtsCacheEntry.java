package com.example.dyslexiaapp.speech;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "tts_cache")
public class TtsCacheEntry {

    @Id
    @Column(name = "cache_hash", nullable = false, length = 64, updatable = false)
    private String cacheHash;

    @Column(name = "voice_id", nullable = false, length = 64)
    private String voiceId;

    @Column(name = "language", nullable = false, length = 16)
    private String language;

    @Column(name = "speed", nullable = false)
    private double speed;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "audio_mime_type", nullable = false, length = 32)
    private String audioMimeType;

    @Column(name = "audio_base64", nullable = false, columnDefinition = "text")
    private String audioBase64;

    @Column(name = "timing_granularity", nullable = false, length = 32)
    private String timingGranularity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speech_marks", nullable = false, columnDefinition = "jsonb")
    private List<SpeechDtos.SpeechMark> speechMarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TtsCacheEntry() {}

    public TtsCacheEntry(String cacheHash, String voiceId, String language, double speed,
                         int characterCount, String audioMimeType, String audioBase64,
                         String timingGranularity, List<SpeechDtos.SpeechMark> speechMarks,
                         Instant createdAt, Instant expiresAt) {
        this.cacheHash = cacheHash;
        this.voiceId = voiceId;
        this.language = language;
        this.speed = speed;
        this.characterCount = characterCount;
        this.audioMimeType = audioMimeType;
        this.audioBase64 = audioBase64;
        this.timingGranularity = timingGranularity;
        this.speechMarks = speechMarks;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCacheHash() { return cacheHash; }
    public String getVoiceId() { return voiceId; }
    public String getLanguage() { return language; }
    public double getSpeed() { return speed; }
    public int getCharacterCount() { return characterCount; }
    public String getAudioMimeType() { return audioMimeType; }
    public String getAudioBase64() { return audioBase64; }
    public String getTimingGranularity() { return timingGranularity; }
    public List<SpeechDtos.SpeechMark> getSpeechMarks() { return speechMarks; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
