CREATE TABLE reading_profiles (
                                  id UUID PRIMARY KEY,
                                  display_name VARCHAR(100) NOT NULL,
                                  share_code VARCHAR(8) UNIQUE,
                                  is_preset BOOLEAN NOT NULL,
                                  profile_json JSONB NOT NULL,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT ck_reading_profiles_preset_share_code
                                      CHECK (
                                          (is_preset = TRUE AND share_code IS NULL)
                                              OR
                                          (is_preset = FALSE AND share_code IS NOT NULL)
                                          )
);

CREATE INDEX idx_reading_profiles_presets
    ON reading_profiles (is_preset, display_name);

INSERT INTO reading_profiles
(id, display_name, share_code, is_preset, profile_json)
VALUES
    (
        '10000000-0000-4000-8000-000000000001',
        'High Contrast',
        NULL,
        TRUE,
        '{
          "schemaVersion": 1,
          "fontFamily": "Atkinson Hyperlegible",
          "fontSizePt": 20,
          "letterSpacingEm": 0.04,
          "wordSpacingEm": 0.1,
          "lineHeight": 1.7,
          "lineWidthCh": 65,
          "theme": "dark-high-contrast",
          "aids": {
            "bionicReading": false,
            "readingRuler": false,
            "bimodalHighlighting": true
          }
        }'::jsonb
    ),
    (
        '10000000-0000-4000-8000-000000000002',
        'Bionic Focus',
        NULL,
        TRUE,
        '{
          "schemaVersion": 1,
          "fontFamily": "Lexend",
          "fontSizePt": 20,
          "letterSpacingEm": 0.02,
          "wordSpacingEm": 0.1,
          "lineHeight": 1.6,
          "lineWidthCh": 65,
          "theme": "warm-cream",
          "aids": {
            "bionicReading": true,
            "readingRuler": false,
            "bimodalHighlighting": true
          }
        }'::jsonb
    ),
    (
        '10000000-0000-4000-8000-000000000003',
        'Large Print Audio',
        NULL,
        TRUE,
        '{
          "schemaVersion": 1,
          "fontFamily": "OpenDyslexic",
          "fontSizePt": 32,
          "letterSpacingEm": 0.05,
          "wordSpacingEm": 0.2,
          "lineHeight": 1.8,
          "lineWidthCh": 50,
          "theme": "sepia",
          "aids": {
            "bionicReading": false,
            "readingRuler": true,
            "bimodalHighlighting": true
          }
        }'::jsonb
    );