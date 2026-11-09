package com.example.dyslexiaapp.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpeechSynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechSynthesisService.class);

    public static final List<SpeechDtos.VoiceInfo> SUPPORTED_VOICES = List.of(
            new SpeechDtos.VoiceInfo(
                    "en-US-Neural2-F",
                    "Aria — English US Neural",
                    "en-US",
                    "English (US)",
                    "FEMALE",
                    "cloud",
                    "NEURAL2",
                    SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                    true
            ),
            new SpeechDtos.VoiceInfo(
                    "en-US-Neural2-D",
                    "Roger — English US Neural",
                    "en-US",
                    "English (US)",
                    "MALE",
                    "cloud",
                    "NEURAL2",
                    SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                    true
            ),
            new SpeechDtos.VoiceInfo(
                    "en-GB-Neural2-A",
                    "Mia — English UK Neural",
                    "en-GB",
                    "English (UK)",
                    "FEMALE",
                    "cloud",
                    "NEURAL2",
                    SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                    true
            ),
            new SpeechDtos.VoiceInfo(
                    "ro-RO-Wavenet-A",
                    "Elena — Română WaveNet",
                    "ro-RO",
                    "Română",
                    "FEMALE",
                    "cloud",
                    "WAVENET",
                    SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                    true
            ),
            new SpeechDtos.VoiceInfo(
                    "ro-RO-Standard-A",
                    "Ioana — Română Standard",
                    "ro-RO",
                    "Română",
                    "FEMALE",
                    "cloud",
                    "STANDARD",
                    SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                    true
            )
    );

    private final GoogleCloudTtsClient client;
    private final SsmlMarkBuilder markBuilder;
    private final TtsCacheRepository repository;
    private final ConcurrentHashMap<String, TtsCacheEntry> l1Cache = new ConcurrentHashMap<>();

    @Value("${dyslexia.tts.cache-ttl-hours:48}")
    private int cacheTtlHours = 48;

    public SpeechSynthesisService(GoogleCloudTtsClient client,
                                  SsmlMarkBuilder markBuilder,
                                  TtsCacheRepository repository) {
        this.client = client;
        this.markBuilder = markBuilder;
        this.repository = repository;
    }

    public SpeechDtos.VoiceCatalogResponse getCatalog() {
        return new SpeechDtos.VoiceCatalogResponse(
                1,
                "cloud",
                "google-cloud-tts",
                client.isAvailable(),
                SUPPORTED_VOICES
        );
    }

    public SpeechDtos.SynthesizeResponse synthesize(SpeechDtos.SynthesizeRequest request) {
        if (request == null || request.text() == null || request.text().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text must not be blank");
        }
        if (request.text().length() > 2500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text length exceeds maximum limit of 2500 characters");
        }

        double resolvedSpeed = (request.speed() != null) ? request.speed() : 1.0;
        if (resolvedSpeed < 0.25 || resolvedSpeed > 4.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Speed must be between 0.25 and 4.0");
        }

        String resolvedVoiceId = request.voiceId();
        if (resolvedVoiceId == null || resolvedVoiceId.trim().isEmpty()) {
            resolvedVoiceId = resolveDefaultVoice(request.language());
        } else {
            resolvedVoiceId = resolvedVoiceId.trim();
        }

        final String voiceIdToLookup = resolvedVoiceId;
        SpeechDtos.VoiceInfo voiceInfo = SUPPORTED_VOICES.stream()
                .filter(v -> v.voiceId().equalsIgnoreCase(voiceIdToLookup))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported voiceId: " + voiceIdToLookup));

        resolvedVoiceId = voiceInfo.voiceId();
        String resolvedLanguage = voiceInfo.language();

        String normalizedText = request.text().trim().replaceAll("\\s+", " ");
        String cacheHash = computeCacheHash(normalizedText, resolvedVoiceId, resolvedSpeed);

        Instant now = Instant.now();

        // 1. Check L1 in-memory cache
        TtsCacheEntry l1Entry = l1Cache.get(cacheHash);
        if (l1Entry != null && l1Entry.getExpiresAt().isAfter(now)) {
            logCacheEvent(cacheHash, resolvedVoiceId, request.text().length(), true);
            return toSynthesizeResponse(l1Entry, true);
        }

        // 2. Check L2 PostgreSQL cache
        if (repository != null) {
            try {
                Optional<TtsCacheEntry> dbEntry = repository.findById(cacheHash);
                if (dbEntry.isPresent()) {
                    TtsCacheEntry entry = dbEntry.get();
                    if (entry.getExpiresAt().isAfter(now)) {
                        l1Cache.put(cacheHash, entry);
                        logCacheEvent(cacheHash, resolvedVoiceId, request.text().length(), true);
                        return toSynthesizeResponse(entry, true);
                    }
                }
            } catch (Exception ex) {
                logger.warn("L2 cache read failed (fallback to live synthesis): {}", ex.getMessage());
            }
        }

        // 3. Cache Miss: synthesize via Google Cloud TTS
        logCacheEvent(cacheHash, resolvedVoiceId, request.text().length(), false);

        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(request.text());
        GoogleCloudTtsClient.SynthesisResult synthesisResult =
                client.synthesize(plan, resolvedVoiceId, resolvedLanguage, resolvedSpeed);

        List<SpeechDtos.SpeechMark> speechMarks =
                markBuilder.buildSpeechMarks(plan, synthesisResult.timepoints(), synthesisResult.estimatedDurationMs());
        SpeechDtos.TimingGranularity timingGranularity = markBuilder.resolveGranularity(speechMarks);

        Instant expiresAt = now.plus(cacheTtlHours, ChronoUnit.HOURS);
        TtsCacheEntry newEntry = new TtsCacheEntry(
                cacheHash,
                resolvedVoiceId,
                resolvedLanguage,
                resolvedSpeed,
                request.text().length(),
                synthesisResult.audioMimeType(),
                synthesisResult.audioContent(),
                timingGranularity.name(),
                speechMarks,
                now,
                expiresAt
        );

        // Store into L1
        l1Cache.put(cacheHash, newEntry);

        // Store into L2
        if (repository != null) {
            try {
                repository.save(newEntry);
            } catch (Exception ex) {
                logger.warn("L2 cache write failed: {}", ex.getMessage());
            }
        }

        return new SpeechDtos.SynthesizeResponse(
                1,
                "cloud",
                resolvedVoiceId,
                resolvedLanguage,
                resolvedSpeed,
                false,
                cacheHash,
                request.text().length(),
                synthesisResult.audioMimeType(),
                synthesisResult.audioContent(),
                timingGranularity,
                speechMarks
        );
    }

    public static String computeCacheHash(String normalizedText, String voiceId, double speed) {
        String formattedSpeed = String.format(Locale.ROOT, "%.2f", speed);
        String rawKey = normalizedText + "|" + voiceId + "|" + formattedSpeed;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hexString.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    public void clearL1Cache() {
        l1Cache.clear();
    }

    private String resolveDefaultVoice(String language) {
        if (language == null) return "en-US-Neural2-F";
        String lower = language.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("en-gb")) {
            return "en-GB-Neural2-A";
        }
        if (lower.startsWith("ro")) {
            return "ro-RO-Wavenet-A";
        }
        return "en-US-Neural2-F";
    }

    private SpeechDtos.SynthesizeResponse toSynthesizeResponse(TtsCacheEntry entry, boolean cacheHit) {
        SpeechDtos.TimingGranularity granularity = safeParseGranularity(entry.getTimingGranularity());
        return new SpeechDtos.SynthesizeResponse(
                1,
                "cloud",
                entry.getVoiceId(),
                entry.getLanguage(),
                entry.getSpeed(),
                cacheHit,
                entry.getCacheHash(),
                entry.getCharacterCount(),
                entry.getAudioMimeType(),
                entry.getAudioBase64(),
                granularity,
                entry.getSpeechMarks()
        );
    }

    private SpeechDtos.TimingGranularity safeParseGranularity(String value) {
        if (value == null) return SpeechDtos.TimingGranularity.NONE;
        try {
            return SpeechDtos.TimingGranularity.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SpeechDtos.TimingGranularity.NONE;
        }
    }

    private void logCacheEvent(String cacheHash, String voiceId, int characterCount, boolean cacheHit) {
        logger.info("Speech synthesis [cacheHash={}, voiceId={}, characterCount={}, cacheHit={}]",
                cacheHash, voiceId, characterCount, cacheHit);
    }
}
