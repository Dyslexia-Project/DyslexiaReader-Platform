package com.example.dyslexiaapp.profile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReadingProfileService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> PROFILE_FIELDS = Set.of("schemaVersion", "fontFamily", "fontSizePt",
            "letterSpacingEm", "wordSpacingEm", "lineHeight", "lineWidthCh", "theme", "aids");
    private static final Set<String> AID_FIELDS = Set.of("bionicReading", "readingRuler", "bimodalHighlighting");
    private static final Set<String> FONTS = Set.of("Lexend", "Atkinson Hyperlegible", "OpenDyslexic");
    private static final Set<String> THEMES = Set.of("warm-cream", "sepia", "dark-high-contrast");

    private final ReadingProfileRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ReadingProfileService(ReadingProfileRepository repository) { this.repository = repository; }

    public List<ProfileResponses.Preset> presets() {
        return repository.findAllByPresetTrueOrderByDisplayNameAsc().stream()
                .map(row -> new ProfileResponses.Preset(row.getDisplayName(), row.getProfile())).toList();
    }

    public String share(Map<String, Object> profile) {
        validate(profile);
        String code = newUniqueCode();
        repository.save(new ReadingProfile(UUID.randomUUID(), "Shared profile", code, false, profile, Instant.now()));
        return code;
    }

    public Map<String, Object> shared(String code) {
        return repository.findByShareCode(code.toUpperCase()).map(ReadingProfile::getProfile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shared profile not found"));
    }

    private String newUniqueCode() {
        String code;
        do {
            StringBuilder value = new StringBuilder(8);
            for (int i = 0; i < 8; i++) value.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            code = value.toString();
        } while (repository.existsByShareCode(code));
        return code;
    }

    private void validate(Map<String, Object> profile) {
        if (profile == null) invalid("Profile must be a JSON object");
        requireExactFields(profile, PROFILE_FIELDS, "profile");
        requireNumber(profile.get("schemaVersion"), "schemaVersion", 1, 1, true);
        requireEnum(profile.get("fontFamily"), "fontFamily", FONTS);
        requireNumber(profile.get("fontSizePt"), "fontSizePt", 14, 48, false);
        requireNumber(profile.get("letterSpacingEm"), "letterSpacingEm", 0, 0.2, false);
        requireNumber(profile.get("wordSpacingEm"), "wordSpacingEm", 0, 0.5, false);
        requireNumber(profile.get("lineHeight"), "lineHeight", 1, 2.5, false);
        requireNumber(profile.get("lineWidthCh"), "lineWidthCh", 35, 100, true);
        requireEnum(profile.get("theme"), "theme", THEMES);

        Object aidsValue = profile.get("aids");
        if (!(aidsValue instanceof Map<?, ?>)) invalid("aids must be an object");
        Map<?, ?> aids = (Map<?, ?>) aidsValue;
        requireExactFields(aids, AID_FIELDS, "aids");
        for (String key : AID_FIELDS) {
            if (!(aids.get(key) instanceof Boolean)) invalid("aids." + key + " must be a boolean");
        }
    }

    private void requireExactFields(Map<?, ?> value, Set<String> expected, String path) {
        Set<String> actual = new HashSet<>();
        for (Object key : value.keySet()) {
            if (!(key instanceof String)) invalid(path + " field names must be strings");
            actual.add((String) key);
        }
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new HashSet<>(actual);
            extra.removeAll(expected);
            if (!missing.isEmpty()) invalid(path + " is missing required fields: " + missing);
            invalid(path + " contains unsupported fields: " + extra);
        }
    }

    private void requireNumber(Object value, String field, double min, double max, boolean integer) {
        if (!(value instanceof Number)) invalid(field + " must be a number");
        double numeric = ((Number) value).doubleValue();
        if (!Double.isFinite(numeric) || numeric < min || numeric > max)
            invalid(field + " must be between " + min + " and " + max);
        if (integer && numeric != Math.rint(numeric)) invalid(field + " must be an integer");
    }

    private void requireEnum(Object value, String field, Set<String> choices) {
        if (!(value instanceof String) || !choices.contains(value)) invalid(field + " must be one of: " + choices);
    }

    private void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
