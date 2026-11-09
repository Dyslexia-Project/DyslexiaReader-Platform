package com.example.dyslexiaapp.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SpeechControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SpeechSynthesisService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpeechController controller = new SpeechController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/speech/voices returns 200 OK with valid catalog JSON schema")
    void testGetVoicesSuccess() throws Exception {
        SpeechDtos.VoiceCatalogResponse mockCatalog = new SpeechDtos.VoiceCatalogResponse(
                1,
                "cloud",
                "google-cloud-tts",
                true,
                List.of(
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
                                "ro-RO-Wavenet-A",
                                "Elena — Română WaveNet",
                                "ro-RO",
                                "Română",
                                "FEMALE",
                                "cloud",
                                "WAVENET",
                                SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                                true
                        )
                )
        );

        when(service.getCatalog()).thenReturn(mockCatalog);

        mockMvc.perform(get("/api/v1/speech/voices")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.provider").value("cloud"))
                .andExpect(jsonPath("$.engine").value("google-cloud-tts"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.voices").isArray())
                .andExpect(jsonPath("$.voices.length()").value(2))
                .andExpect(jsonPath("$.voices[0].voiceId").value("en-US-Neural2-F"))
                .andExpect(jsonPath("$.voices[0].timingGranularity").value("WORD_AND_SENTENCE"))
                .andExpect(jsonPath("$.voices[0].supportsWordTimestamps").value(true))
                .andExpect(jsonPath("$.voices[1].voiceId").value("ro-RO-Wavenet-A"))
                .andExpect(jsonPath("$.voices[1].language").value("ro-RO"));
    }

    @Test
    @DisplayName("POST /api/v1/speech/synthesize returns 200 OK with audio and speech marks")
    void testSynthesizeSuccess() throws Exception {
        SpeechDtos.SynthesizeRequest request = new SpeechDtos.SynthesizeRequest(
                "Dyslexia Reader works offline first.",
                "en-US-Neural2-F",
                1.0,
                "en-US"
        );

        SpeechDtos.SynthesizeResponse mockResponse = new SpeechDtos.SynthesizeResponse(
                1,
                "cloud",
                "en-US-Neural2-F",
                "en-US",
                1.0,
                false,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                36,
                "audio/mpeg",
                "//NExAAAAANIAAAAAExBTUUzLjEwMKqq...",
                SpeechDtos.TimingGranularity.WORD_AND_SENTENCE,
                List.of(
                        new SpeechDtos.SpeechMark(0, 2150, "sentence", 0, 36, "Dyslexia Reader works offline first."),
                        new SpeechDtos.SpeechMark(0, 410, "word", 0, 8, "Dyslexia"),
                        new SpeechDtos.SpeechMark(410, 330, "word", 9, 15, "Reader")
                )
        );

        when(service.synthesize(any(SpeechDtos.SynthesizeRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.provider").value("cloud"))
                .andExpect(jsonPath("$.voiceId").value("en-US-Neural2-F"))
                .andExpect(jsonPath("$.language").value("en-US"))
                .andExpect(jsonPath("$.speed").value(1.0))
                .andExpect(jsonPath("$.cacheHit").value(false))
                .andExpect(jsonPath("$.cacheHash").value("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
                .andExpect(jsonPath("$.characterCount").value(36))
                .andExpect(jsonPath("$.audioMimeType").value("audio/mpeg"))
                .andExpect(jsonPath("$.audioBase64").isString())
                .andExpect(jsonPath("$.timingGranularity").value("WORD_AND_SENTENCE"))
                .andExpect(jsonPath("$.speechMarks").isArray())
                .andExpect(jsonPath("$.speechMarks.length()").value(3))
                .andExpect(jsonPath("$.speechMarks[0].type").value("sentence"))
                .andExpect(jsonPath("$.speechMarks[1].type").value("word"))
                .andExpect(jsonPath("$.speechMarks[1].value").value("Dyslexia"));
    }

    @Test
    @DisplayName("POST /api/v1/speech/synthesize returns 400 Bad Request on validation error")
    void testSynthesizeBadRequest() throws Exception {
        SpeechDtos.SynthesizeRequest invalidRequest = new SpeechDtos.SynthesizeRequest(
                "",
                "en-US-Neural2-F",
                1.0,
                "en-US"
        );

        when(service.synthesize(any(SpeechDtos.SynthesizeRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text must not be blank"));

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fallbackRecommendation").value("CHECK_PARAMETERS"))
                .andExpect(jsonPath("$.message").value("Text must not be blank"));
    }

    @Test
    @DisplayName("POST /api/v1/speech/synthesize returns 503 Service Unavailable when CloudTtsUnavailableException is thrown")
    void testSynthesizeCloudTtsUnavailable() throws Exception {
        SpeechDtos.SynthesizeRequest request = new SpeechDtos.SynthesizeRequest(
                "Offline text synthesis request",
                "en-US-Neural2-F",
                1.0,
                "en-US"
        );

        String failureMessage = "Google Cloud TTS API key is not configured. Please set GOOGLE_CLOUD_API_KEY or use local speech synthesis.";
        when(service.synthesize(any(SpeechDtos.SynthesizeRequest.class)))
                .thenThrow(new CloudTtsUnavailableException(failureMessage));

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("CLOUD_TTS_UNAVAILABLE"))
                .andExpect(jsonPath("$.fallbackRecommendation").value("LOCAL_TTS"))
                .andExpect(jsonPath("$.message").value(failureMessage));
    }
}
