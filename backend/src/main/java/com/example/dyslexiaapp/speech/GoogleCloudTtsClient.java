package com.example.dyslexiaapp.speech;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class GoogleCloudTtsClient {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudTtsClient.class);

    public record SynthesisResult(
            String audioContent,
            String audioMimeType,
            List<SsmlMarkBuilder.Timepoint> timepoints,
            double estimatedDurationMs
    ) {}

    @Value("${dyslexia.tts.google.api-key:}")
    private String apiKey;

    @Value("${dyslexia.tts.google.endpoint:https://texttospeech.googleapis.com/v1beta1/text:synthesize}")
    private String endpoint;

    @Value("${dyslexia.tts.demo-mode:false}")
    private boolean demoMode;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 1. Constructor por defecto para Spring (instancia new ObjectMapper())
    public GoogleCloudTtsClient() {
        this(new ObjectMapper());
    }

    // 2. Constructor secundario
    public GoogleCloudTtsClient(ObjectMapper objectMapper) {
        this(objectMapper, null, "https://texttospeech.googleapis.com/v1beta1/text:synthesize", true);
    }

    // 3. Constructor principal
    public GoogleCloudTtsClient(ObjectMapper objectMapper, String apiKey, String endpoint, boolean demoMode) {
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.apiKey = apiKey;
        this.endpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : "https://texttospeech.googleapis.com/v1beta1/text:synthesize";
        this.demoMode = demoMode;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public boolean isAvailable() {
        return (apiKey != null && !apiKey.isBlank()) || demoMode;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public SynthesisResult synthesize(SsmlMarkBuilder.SsmlPlan plan, String voiceId, String language, double speed) {
        if (apiKey == null || apiKey.isBlank()) {
            if (demoMode) {
                return synthesizeDemo(plan, speed);
            }
            throw new CloudTtsUnavailableException(
                    "Google Cloud TTS API key is not configured. Please set GOOGLE_CLOUD_API_KEY or use local speech synthesis."
            );
        }

        try {
            Map<String, Object> payloadMap = Map.of(
                    "input", Map.of("ssml", plan.ssml()),
                    "voice", Map.of("languageCode", language, "name", voiceId),
                    "audioConfig", Map.of("audioEncoding", "MP3", "speakingRate", speed),
                    "enableTimePointing", List.of("SSML_MARK")
            );

            String requestBody = objectMapper.writeValueAsString(payloadMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Goog-Api-Key", apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                logger.error("Google Cloud TTS synthesis failed with HTTP status {}", response.statusCode());
                throw new CloudTtsUnavailableException(
                        "Google Cloud TTS request failed with HTTP " + response.statusCode() + ": " + response.body()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String audioContent = root.path("audioContent").asText("");
            if (audioContent.isBlank()) {
                throw new CloudTtsUnavailableException("Google Cloud TTS response missing audioContent");
            }

            List<SsmlMarkBuilder.Timepoint> timepoints = new ArrayList<>();
            JsonNode timepointsNode = root.path("timepoints");
            double maxTimeSec = 0.0;
            if (timepointsNode.isArray()) {
                for (JsonNode tp : timepointsNode) {
                    String markName = tp.path("markName").asText("");
                    double timeSeconds = tp.path("timeSeconds").asDouble(0.0);
                    timepoints.add(new SsmlMarkBuilder.Timepoint(markName, timeSeconds));
                    if (timeSeconds > maxTimeSec) {
                        maxTimeSec = timeSeconds;
                    }
                }
            }

            double estimatedDurationMs = (maxTimeSec * 1000.0) + (400.0 / Math.max(0.25, speed));
            return new SynthesisResult(audioContent, "audio/mpeg", timepoints, estimatedDurationMs);

        } catch (CloudTtsUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudTtsUnavailableException("Google Cloud TTS request failed: " + e.getMessage(), e);
        }
    }

    private SynthesisResult synthesizeDemo(SsmlMarkBuilder.SsmlPlan plan, double speed) {
        double rate = Math.max(0.25, Math.min(4.0, speed));
        double wordDurationSec = 0.320 * (1.0 / rate);
        double sentencePauseSec = 0.180 * (1.0 / rate);

        List<SsmlMarkBuilder.Timepoint> timepoints = new ArrayList<>();
        double currentTimeSec = 0.0;

        int wordIndex = 0;
        List<SsmlMarkBuilder.MarkToken> sentences = plan.sentences();
        List<SsmlMarkBuilder.MarkToken> words = plan.words();

        for (SsmlMarkBuilder.MarkToken sToken : sentences) {
            timepoints.add(new SsmlMarkBuilder.Timepoint(sToken.id(), currentTimeSec));

            while (wordIndex < words.size()) {
                SsmlMarkBuilder.MarkToken wToken = words.get(wordIndex);
                if (wToken.start() >= sToken.end()) {
                    break;
                }
                timepoints.add(new SsmlMarkBuilder.Timepoint(wToken.id(), currentTimeSec));
                currentTimeSec += wordDurationSec;
                wordIndex++;
            }
            currentTimeSec += sentencePauseSec;
        }

        while (wordIndex < words.size()) {
            SsmlMarkBuilder.MarkToken wToken = words.get(wordIndex);
            timepoints.add(new SsmlMarkBuilder.Timepoint(wToken.id(), currentTimeSec));
            currentTimeSec += wordDurationSec;
            wordIndex++;
        }

        // Minimal valid silent MP3 frame (MPEG-1 Layer III, 128 kbps, 44.1 kHz, 418 bytes)
        byte[] silentMp3 = new byte[418];
        silentMp3[0] = (byte) 0xFF;
        silentMp3[1] = (byte) 0xFB;
        silentMp3[2] = (byte) 0x90;
        silentMp3[3] = (byte) 0x00;
        String demoAudioBase64 = Base64.getEncoder().encodeToString(silentMp3);

        return new SynthesisResult(demoAudioBase64, "audio/mpeg", timepoints, currentTimeSec * 1000.0);
    }
}
