package com.example.dyslexiaapp.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/profiles")
@Tag(name = "Reading Profiles", description = "Public Sprint 1 preset and profile-sharing API")
public class ReadingProfileController {
    private final ReadingProfileService service;

    public ReadingProfileController(ReadingProfileService service) { this.service = service; }

    @GetMapping("/presets")
    @Operation(summary = "List built-in accessibility presets")
    public List<ProfileResponses.Preset> presets() { return service.presets(); }

    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Validate and save a profile, returning its share code")
    public ProfileResponses.Shared share(@RequestBody Map<String, Object> profile) {
        return new ProfileResponses.Shared(service.share(profile));
    }

    @GetMapping("/share/{code}")
    @Operation(summary = "Retrieve a shared profile by code")
    public Map<String, Object> shared(@PathVariable String code) { return service.shared(code); }
}
