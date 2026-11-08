package com.example.dyslexiaapp.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechSynthesisServiceTest {

    @Mock
    private GoogleCloudTtsClient client;

    @Mock
    private TtsCacheRepository repository;

    @Spy
    private SsmlMarkBuilder markBuilder = new SsmlMarkBuilder();

    private SpeechSynthesisService service;

    @BeforeEach
    void setUp() {
        service = new SpeechSynthesisService(client, markBuilder, repository);
    }

    @Test
    @DisplayName("SHA-256 normalization: identical text with varied whitespace shares cacheHash and invokes client only once")
    void testSha256NormalizationAndL1Caching() {
        String rawText1 = "Hello   world";
        String rawText2 = "  Hello world  ";
        String voiceId = "en-US-Neural2-F";
        double speed = 1.0;

        // SHA-256 hash must be identical for normalized strings
        String hash1 = SpeechSynthesisService.computeCacheHash("Hello world", voiceId, speed);
        String hash2 = SpeechSynthesisService.computeCacheHash("Hello world", voiceId, speed);
        assertThat(hash1).isEqualTo(hash2);

        // Setup mock synthesis result for the first miss
        GoogleCloudTtsClient.SynthesisResult mockResult = new GoogleCloudTtsClient.SynthesisResult(
                "dGVzdEF1ZGlvQmFzZTY0",
                "audio/mpeg",
                List.of(
                        new SsmlMarkBuilder.Timepoint("s_0", 0.0),
                        new SsmlMarkBuilder.Timepoint("w_0", 0.0),
                        new SsmlMarkBuilder.Timepoint("w_1", 0.35)
                ),
                1200.0
        );

        when(client.synthesize(any(SsmlMarkBuilder.SsmlPlan.class), eq(voiceId), eq("en-US"), eq(speed)))
                .thenReturn(mockResult);

        // First call: cache miss, triggers synthesis
        SpeechDtos.SynthesizeRequest request1 = new SpeechDtos.SynthesizeRequest(rawText1, voiceId, speed, "en-US");
        SpeechDtos.SynthesizeResponse response1 = service.synthesize(request1);

        assertThat(response1.cacheHit()).isFalse();
        assertThat(response1.cacheHash()).isEqualTo(hash1);
        assertThat(response1.audioBase64()).isEqualTo("dGVzdEF1ZGlvQmFzZTY0");
        assertThat(response1.timingGranularity()).isEqualTo(SpeechDtos.TimingGranularity.WORD_AND_SENTENCE);

        // Second call with different whitespace: hits L1 cache, client must NOT be called again
        SpeechDtos.SynthesizeRequest request2 = new SpeechDtos.SynthesizeRequest(rawText2, voiceId, speed, "en-US");
        SpeechDtos.SynthesizeResponse response2 = service.synthesize(request2);

        assertThat(response2.cacheHit()).isTrue();
        assertThat(response2.cacheHash()).isEqualTo(hash1);
        assertThat(response2.audioBase64()).isEqualTo("dGVzdEF1ZGlvQmFzZTY0");

        // Google Cloud TTS client invoked only ONCE across both calls
        verify(client, times(1)).synthesize(any(SsmlMarkBuilder.SsmlPlan.class), anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("L2 PostgreSQL cache hit returns cached audio without calling Google Cloud TTS client")
    void testL2CacheHit() {
        String text = "Read this from persistent database cache";
        String voiceId = "en-US-Neural2-F";
        double speed = 1.0;
        String cacheHash = SpeechSynthesisService.computeCacheHash(text, voiceId, speed);

        TtsCacheEntry dbEntry = new TtsCacheEntry(
                cacheHash,
                voiceId,
                "en-US",
                speed,
                text.length(),
                "audio/mpeg",
                "cGVyc2lzdGVudEF1ZGlv",
                SpeechDtos.TimingGranularity.WORD_AND_SENTENCE.name(),
                List.of(new SpeechDtos.SpeechMark(0, 500, "sentence", 0, text.length(), text)),
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        // Service L1 cache is empty initially, repository returns dbEntry
        when(repository.findById(cacheHash)).thenReturn(Optional.of(dbEntry));

        SpeechDtos.SynthesizeRequest request = new SpeechDtos.SynthesizeRequest(text, voiceId, speed, "en-US");
        SpeechDtos.SynthesizeResponse response = service.synthesize(request);

        assertThat(response.cacheHit()).isTrue();
        assertThat(response.cacheHash()).isEqualTo(cacheHash);
        assertThat(response.audioBase64()).isEqualTo("cGVyc2lzdGVudEF1ZGlv");

        verify(client, never()).synthesize(any(), anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Voice catalog returns English (en-US, en-GB) and Romanian (ro-RO) voices")
    void testVoiceCatalogContainsEnglishAndRomanianVoices() {
        when(client.isAvailable()).thenReturn(true);

        SpeechDtos.VoiceCatalogResponse catalog = service.getCatalog();

        assertThat(catalog.schemaVersion()).isEqualTo(1);
        assertThat(catalog.provider()).isEqualTo("cloud");
        assertThat(catalog.available()).isTrue();
        assertThat(catalog.voices()).isNotEmpty();

        List<String> voiceIds = catalog.voices().stream().map(SpeechDtos.VoiceInfo::voiceId).toList();
        assertThat(voiceIds).contains(
                "en-US-Neural2-F",
                "en-US-Neural2-D",
                "en-GB-Neural2-A",
                "ro-RO-Wavenet-A",
                "ro-RO-Standard-A"
        );

        List<String> languages = catalog.voices().stream().map(SpeechDtos.VoiceInfo::language).toList();
        assertThat(languages).contains("en-US", "en-GB", "ro-RO");

        // All supported voices support word timestamps and WORD_AND_SENTENCE granularity
        for (SpeechDtos.VoiceInfo voice : catalog.voices()) {
            assertThat(voice.supportsWordTimestamps()).isTrue();
            assertThat(voice.timingGranularity()).isEqualTo(SpeechDtos.TimingGranularity.WORD_AND_SENTENCE);
        }
    }

    @Test
    @DisplayName("Validation failure: blank or null text throws BAD_REQUEST")
    void testValidationFailsForBlankText() {
        assertThatThrownBy(() -> service.synthesize(new SpeechDtos.SynthesizeRequest("", "en-US-Neural2-F", 1.0, "en-US")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.synthesize(new SpeechDtos.SynthesizeRequest("    ", "en-US-Neural2-F", 1.0, "en-US")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.synthesize(new SpeechDtos.SynthesizeRequest(null, "en-US-Neural2-F", 1.0, "en-US")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Validation failure: text exceeding 2500 characters throws BAD_REQUEST")
    void testValidationFailsForTextExceedingLimit() {
        String longText = "A".repeat(2501);
        SpeechDtos.SynthesizeRequest request = new SpeechDtos.SynthesizeRequest(longText, "en-US-Neural2-F", 1.0, "en-US");

        assertThatThrownBy(() -> service.synthesize(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("maximum limit of 2500 characters");
                });
    }

    @Test
    @DisplayName("Validation failure: speed outside 0.25 to 4.0 range throws BAD_REQUEST")
    void testValidationFailsForInvalidSpeed() {
        SpeechDtos.SynthesizeRequest tooSlow = new SpeechDtos.SynthesizeRequest("Hello", "en-US-Neural2-F", 0.1, "en-US");
        assertThatThrownBy(() -> service.synthesize(tooSlow))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        SpeechDtos.SynthesizeRequest tooFast = new SpeechDtos.SynthesizeRequest("Hello", "en-US-Neural2-F", 5.0, "en-US");
        assertThatThrownBy(() -> service.synthesize(tooFast))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Validation failure: unsupported voiceId throws BAD_REQUEST")
    void testValidationFailsForUnsupportedVoiceId() {
        SpeechDtos.SynthesizeRequest request = new SpeechDtos.SynthesizeRequest("Hello", "unsupported-alien-voice", 1.0, "en-US");

        assertThatThrownBy(() -> service.synthesize(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Unsupported voiceId");
                });
    }

    @Test
    @DisplayName("Default voice resolution selects appropriate voices for language prefixes")
    void testDefaultVoiceResolution() {
        GoogleCloudTtsClient.SynthesisResult mockResult = new GoogleCloudTtsClient.SynthesisResult(
                "YXVkaW8=", "audio/mpeg", List.of(), 500.0
        );
        when(client.synthesize(any(), anyString(), anyString(), anyDouble())).thenReturn(mockResult);

        // null voiceId with language "ro-RO" defaults to "ro-RO-Wavenet-A"
        SpeechDtos.SynthesizeResponse roRes = service.synthesize(new SpeechDtos.SynthesizeRequest("Salut", null, 1.0, "ro-RO"));
        assertThat(roRes.voiceId()).isEqualTo("ro-RO-Wavenet-A");

        // null voiceId with language "en-GB" defaults to "en-GB-Neural2-A"
        SpeechDtos.SynthesizeResponse gbRes = service.synthesize(new SpeechDtos.SynthesizeRequest("Cheerio", null, 1.0, "en-GB"));
        assertThat(gbRes.voiceId()).isEqualTo("en-GB-Neural2-A");

        // null voiceId with null language defaults to "en-US-Neural2-F"
        SpeechDtos.SynthesizeResponse usRes = service.synthesize(new SpeechDtos.SynthesizeRequest("Hi", null, 1.0, null));
        assertThat(usRes.voiceId()).isEqualTo("en-US-Neural2-F");
    }

    @Test
    @DisplayName("GoogleCloudTtsClient demo mode returns synthesized dummy audio when apiKey is blank and demoMode=true")
    void testGoogleCloudTtsClientDemoModeEnabled() {
        ObjectMapper objectMapper = new ObjectMapper();
        GoogleCloudTtsClient clientWithDemo = new GoogleCloudTtsClient(
                objectMapper, "", "https://texttospeech.googleapis.com/v1beta1/text:synthesize", true
        );

        assertThat(clientWithDemo.isAvailable()).isTrue();

        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan("This is a demo test.");
        GoogleCloudTtsClient.SynthesisResult result = clientWithDemo.synthesize(plan, "en-US-Neural2-F", "en-US", 1.0);

        assertThat(result.audioContent()).isNotBlank();
        assertThat(result.audioMimeType()).isEqualTo("audio/mpeg");
        assertThat(result.timepoints()).isNotEmpty();
        assertThat(result.estimatedDurationMs()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("GoogleCloudTtsClient throws CloudTtsUnavailableException when apiKey is blank and demoMode=false")
    void testGoogleCloudTtsClientDemoModeDisabledThrowsException() {
        ObjectMapper objectMapper = new ObjectMapper();
        GoogleCloudTtsClient unconfiguredClient = new GoogleCloudTtsClient(
                objectMapper, "", "https://texttospeech.googleapis.com/v1beta1/text:synthesize", false
        );

        assertThat(unconfiguredClient.isAvailable()).isFalse();

        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan("This should fail.");
        assertThatThrownBy(() -> unconfiguredClient.synthesize(plan, "en-US-Neural2-F", "en-US", 1.0))
                .isInstanceOf(CloudTtsUnavailableException.class)
                .hasMessageContaining("Google Cloud TTS API key is not configured");
    }

    @Test
    @DisplayName("Live Google Cloud TTS verification when .env is present in platform/")
    void testLiveGoogleCloudTtsWhenEnvPresent() throws Exception {
        java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
        if (!java.nio.file.Files.exists(envPath)) {
            envPath = java.nio.file.Paths.get("../.env");
        }
        if (!java.nio.file.Files.exists(envPath)) {
            return;
        }
        String apiKey = java.nio.file.Files.readAllLines(envPath).stream()
                .map(String::trim)
                .filter(l -> !l.startsWith("#") && l.contains("="))
                .map(l -> l.substring(l.indexOf('=') + 1).trim().replaceAll("^['\"]|['\"]$", ""))
                .findFirst()
                .orElse("");
        if (apiKey.isBlank()) {
            return;
        }
        if (apiKey.contains("=")) {
            apiKey = apiKey.substring(apiKey.indexOf('=') + 1).trim();
        }

        GoogleCloudTtsClient liveClient = new GoogleCloudTtsClient(
                new ObjectMapper(),
                apiKey,
                "https://texttospeech.googleapis.com/v1beta1/text:synthesize",
                false
        );
        SpeechSynthesisService liveService = new SpeechSynthesisService(liveClient, markBuilder, repository);

        // 1. Live English synthesis
        SpeechDtos.SynthesizeResponse enRes = liveService.synthesize(
                new SpeechDtos.SynthesizeRequest("Dyslexia Reader works great.", "en-US-Neural2-F", 1.0, "en-US")
        );
        assertThat(enRes.cacheHit()).isFalse();
        assertThat(enRes.audioBase64()).isNotBlank();
        assertThat(enRes.timingGranularity()).isEqualTo(SpeechDtos.TimingGranularity.WORD_AND_SENTENCE);
        assertThat(enRes.speechMarks()).isNotEmpty();

        // 2. Second call hits SHA-256 cache immediately (0 extra API calls!)
        SpeechDtos.SynthesizeResponse enCached = liveService.synthesize(
                new SpeechDtos.SynthesizeRequest("  Dyslexia   Reader works great. ", "en-US-Neural2-F", 1.0, "en-US")
        );
        assertThat(enCached.cacheHit()).isTrue();
        assertThat(enCached.cacheHash()).isEqualTo(enRes.cacheHash());

        // 3. Live Romanian synthesis with diacritics
        SpeechDtos.SynthesizeResponse roRes = liveService.synthesize(
                new SpeechDtos.SynthesizeRequest("Cititul este mai ușor acum.", "ro-RO-Wavenet-A", 1.0, "ro-RO")
        );
        assertThat(roRes.cacheHit()).isFalse();
        assertThat(roRes.audioBase64()).isNotBlank();
        assertThat(roRes.timingGranularity()).isEqualTo(SpeechDtos.TimingGranularity.WORD_AND_SENTENCE);
        assertThat(roRes.speechMarks()).isNotEmpty();

        System.out.println("=== LIVE GOOGLE CLOUD TTS RESULTS ===");
        System.out.println("English Audio Base64 length: " + enRes.audioBase64().length() + " chars, Marks: " + enRes.speechMarks());
        System.out.println("English Repeat Cache Hit: " + enCached.cacheHit() + " (Hash: " + enCached.cacheHash() + ")");
        System.out.println("Romanian Audio Base64 length: " + roRes.audioBase64().length() + " chars, Marks: " + roRes.speechMarks());
        java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"));
        java.nio.file.Files.write(tempDir.resolve("dyslexia_en_sample.mp3"), java.util.Base64.getDecoder().decode(enRes.audioBase64()));
        java.nio.file.Files.write(tempDir.resolve("dyslexia_ro_sample.mp3"), java.util.Base64.getDecoder().decode(roRes.audioBase64()));
    }
}
