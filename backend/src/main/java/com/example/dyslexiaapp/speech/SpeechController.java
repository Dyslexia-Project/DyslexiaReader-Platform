package com.example.dyslexiaapp.speech;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/speech")
@CrossOrigin(origins = "*")
@Tag(name = "Speech Synthesis", description = "Cloud TTS and timing synchronization API")
public class SpeechController {

    private final SpeechSynthesisService service;

    public SpeechController(SpeechSynthesisService service) {
        this.service = service;
    }

    @GetMapping("/voices")
    @Operation(summary = "List supported Cloud TTS voices and timing capabilities")
    public SpeechDtos.VoiceCatalogResponse voices() {
        return service.getCatalog();
    }

    @PostMapping("/synthesize")
    @Operation(summary = "Synthesize speech with word and sentence timestamps and server caching")
    public SpeechDtos.SynthesizeResponse synthesize(
            @RequestBody SpeechDtos.SynthesizeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return service.synthesize(request);
    }

    @ExceptionHandler(CloudTtsUnavailableException.class)
    public ResponseEntity<SpeechDtos.SpeechErrorResponse> handleCloudTtsUnavailable(CloudTtsUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new SpeechDtos.SpeechErrorResponse("CLOUD_TTS_UNAVAILABLE", "LOCAL_TTS", ex.getMessage())
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<SpeechDtos.SpeechErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(
                new SpeechDtos.SpeechErrorResponse("INVALID_REQUEST", "CHECK_PARAMETERS", reason)
        );
    }
}
