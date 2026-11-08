package com.example.dyslexiaapp.speech;

import java.util.List;

public final class SpeechDtos {

    private SpeechDtos() {}

    public enum TimingGranularity {
        WORD_AND_SENTENCE,
        SENTENCE_ONLY,
        NONE
    }

    public record SynthesizeRequest(
            String text,
            String voiceId,
            Double speed,
            String language
    ) {}

    public record SpeechMark(
            long time,
            long duration,
            String type,
            int start,
            int end,
            String value
    ) {}

    public record SynthesizeResponse(
            int schemaVersion,
            String provider,
            String voiceId,
            String language,
            double speed,
            boolean cacheHit,
            String cacheHash,
            int characterCount,
            String audioMimeType,
            String audioBase64,
            TimingGranularity timingGranularity,
            List<SpeechMark> speechMarks
    ) {}

    public record VoiceInfo(
            String voiceId,
            String displayName,
            String language,
            String languageName,
            String gender,
            String provider,
            String quality,
            TimingGranularity timingGranularity,
            boolean supportsWordTimestamps
    ) {}

    public record VoiceCatalogResponse(
            int schemaVersion,
            String provider,
            String engine,
            boolean available,
            List<VoiceInfo> voices
    ) {}

    public record SpeechErrorResponse(
            String error,
            String fallbackRecommendation,
            String message
    ) {}
}
