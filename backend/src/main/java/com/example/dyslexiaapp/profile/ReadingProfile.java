package com.example.dyslexiaapp.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reading_profiles")
public class ReadingProfile {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    @Column(name = "share_code", unique = true, length = 8)
    private String shareCode;
    @Column(name = "is_preset", nullable = false)
    private boolean preset;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> profile;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReadingProfile() {}

    public ReadingProfile(UUID id, String displayName, String shareCode, boolean preset,
                          Map<String, Object> profile, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.shareCode = shareCode;
        this.preset = preset;
        this.profile = profile;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getShareCode() { return shareCode; }
    public boolean isPreset() { return preset; }
    public Map<String, Object> getProfile() { return profile; }
    public Instant getCreatedAt() { return createdAt; }
}
