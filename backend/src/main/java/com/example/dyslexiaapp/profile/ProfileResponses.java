package com.example.dyslexiaapp.profile;

import java.util.Map;

public final class ProfileResponses {
    private ProfileResponses() {}
    public record Preset(String displayName, Map<String, Object> profile) {}
    public record Shared(String code) {}
}
