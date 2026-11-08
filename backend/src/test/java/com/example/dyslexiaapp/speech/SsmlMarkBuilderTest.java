package com.example.dyslexiaapp.speech;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SsmlMarkBuilderTest {

    private SsmlMarkBuilder markBuilder;

    @BeforeEach
    void setUp() {
        markBuilder = new SsmlMarkBuilder();
    }

    @Test
    @DisplayName("SSML plan preserves exact UTF-16 offsets for English text and escapes XML")
    void testEnglishTextOffsetIntegrity() {
        String text = "Dyslexia Reader works offline first. Cloud voices add neural speech.";

        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(text);

        assertThat(plan.ssml()).startsWith("<speak>").endsWith("</speak>");
        assertThat(plan.sentences()).hasSize(2);
        assertThat(plan.words()).isNotEmpty();

        // Verify all sentence token offsets match rawText
        for (SsmlMarkBuilder.MarkToken sentence : plan.sentences()) {
            assertThat(sentence.type()).isEqualTo("sentence");
            String extracted = text.substring(sentence.start(), sentence.end());
            assertThat(extracted)
                    .as("Sentence token substring at [%d, %d] must match token value", sentence.start(), sentence.end())
                    .isEqualTo(sentence.value());
        }

        // Verify all word token offsets match rawText
        for (SsmlMarkBuilder.MarkToken word : plan.words()) {
            assertThat(word.type()).isEqualTo("word");
            String extracted = text.substring(word.start(), word.end());
            assertThat(extracted)
                    .as("Word token substring at [%d, %d] must match token value", word.start(), word.end())
                    .isEqualTo(word.value());
        }

        // First sentence verification
        SsmlMarkBuilder.MarkToken firstSentence = plan.sentences().get(0);
        assertThat(firstSentence.value()).isEqualTo("Dyslexia Reader works offline first.");
        assertThat(firstSentence.start()).isEqualTo(0);
        assertThat(firstSentence.end()).isEqualTo(36);

        // First word verification
        SsmlMarkBuilder.MarkToken firstWord = plan.words().get(0);
        assertThat(firstWord.value()).isEqualTo("Dyslexia");
        assertThat(firstWord.start()).isEqualTo(0);
        assertThat(firstWord.end()).isEqualTo(8);
    }

    @Test
    @DisplayName("SSML plan handles Romanian text with diacritics and XML special characters without shifting offsets")
    void testRomanianTextWithDiacriticsAndXmlSpecialChars() {
        // Includes ă, â, î, ș, ț and XML special chars: &, <, >, ", '
        String rawText = "Copiii învață & citesc cu plăcere! Școala este o aventură > o simplă sarcină; prețul este < 100 lei, iar 'succesul' este \"garantat\".";

        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(rawText);

        // Verify SSML contains valid XML escaping
        assertThat(plan.ssml()).contains("&amp;");
        assertThat(plan.ssml()).contains("&gt;");
        assertThat(plan.ssml()).contains("&lt;");
        assertThat(plan.ssml()).contains("&apos;<mark name=\"w_17\"/>succesul&apos;");
        assertThat(plan.ssml()).contains("&quot;<mark name=\"w_19\"/>garantat&quot;");

        // Verify raw unescaped XML chars do not appear outside markup tags
        assertThat(plan.ssml()).doesNotContain(" & ");
        assertThat(plan.ssml()).doesNotContain(" > ");
        assertThat(plan.ssml()).doesNotContain(" < 100");

        // Verify Romanian diacritic sentences and words preserve exact character offsets
        assertThat(plan.sentences()).hasSizeGreaterThanOrEqualTo(2);
        for (SsmlMarkBuilder.MarkToken sentence : plan.sentences()) {
            String slice = rawText.substring(sentence.start(), sentence.end());
            assertThat(slice)
                    .as("Sentence '%s' substring must equal value despite XML escaping in SSML", sentence.value())
                    .isEqualTo(sentence.value());
        }

        for (SsmlMarkBuilder.MarkToken word : plan.words()) {
            String slice = rawText.substring(word.start(), word.end());
            assertThat(slice)
                    .as("Word '%s' at [%d, %d] must match raw text exactly", word.value(), word.start(), word.end())
                    .isEqualTo(word.value());
        }

        // Verify specific Romanian words with diacritics
        List<String> wordValues = plan.words().stream().map(SsmlMarkBuilder.MarkToken::value).toList();
        assertThat(wordValues).contains("învață", "citesc", "plăcere", "Școala", "aventură", "sarcină", "prețul");
    }

    @Test
    @DisplayName("buildSpeechMarks converts timeSeconds to milliseconds and preserves offsets")
    void testBuildSpeechMarksTimeConversionAndOffsetIntegrity() {
        String text = "Hello world! Welcome to speech.";
        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(text);

        // Fake timepoints returned by Google Cloud TTS (in seconds)
        List<SsmlMarkBuilder.Timepoint> timepoints = List.of(
                new SsmlMarkBuilder.Timepoint("s_0", 0.0),
                new SsmlMarkBuilder.Timepoint("w_0", 0.0),     // "Hello"
                new SsmlMarkBuilder.Timepoint("w_1", 0.425),   // "world"
                new SsmlMarkBuilder.Timepoint("s_1", 1.200),
                new SsmlMarkBuilder.Timepoint("w_2", 1.200),   // "Welcome"
                new SsmlMarkBuilder.Timepoint("w_3", 1.650),   // "to"
                new SsmlMarkBuilder.Timepoint("w_4", 1.850)    // "speech"
        );

        List<SpeechDtos.SpeechMark> speechMarks = markBuilder.buildSpeechMarks(plan, timepoints, 2500.0);

        assertThat(speechMarks).isNotEmpty();

        // Check millisecond conversion
        SpeechDtos.SpeechMark worldMark = speechMarks.stream()
                .filter(m -> "world".equals(m.value()))
                .findFirst()
                .orElseThrow();
        assertThat(worldMark.time()).isEqualTo(425L);
        assertThat(worldMark.type()).isEqualTo("word");

        // Verify that every SpeechMark start and end offset matches text.substring
        for (SpeechDtos.SpeechMark mark : speechMarks) {
            String extracted = text.substring(mark.start(), mark.end());
            assertThat(extracted)
                    .as("SpeechMark '%s' offset slice must match value", mark.value())
                    .isEqualTo(mark.value());
        }
    }

    @Test
    @DisplayName("buildSpeechMarks sorts sentence marks before word marks when timestamps are identical")
    void testSentenceMarksSortBeforeWordMarksAtSameTimestamp() {
        String text = "Alpha beta.";
        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(text);

        List<SsmlMarkBuilder.Timepoint> timepoints = List.of(
                new SsmlMarkBuilder.Timepoint("w_0", 0.0), // Alpha (word)
                new SsmlMarkBuilder.Timepoint("s_0", 0.0), // Alpha beta. (sentence)
                new SsmlMarkBuilder.Timepoint("w_1", 0.5)  // beta (word)
        );

        List<SpeechDtos.SpeechMark> speechMarks = markBuilder.buildSpeechMarks(plan, timepoints, 1000.0);

        assertThat(speechMarks).hasSize(3);
        // At timestamp 0, sentence mark MUST appear before word mark
        assertThat(speechMarks.get(0).type()).isEqualTo("sentence");
        assertThat(speechMarks.get(0).value()).isEqualTo("Alpha beta.");
        assertThat(speechMarks.get(0).time()).isEqualTo(0L);

        assertThat(speechMarks.get(1).type()).isEqualTo("word");
        assertThat(speechMarks.get(1).value()).isEqualTo("Alpha");
        assertThat(speechMarks.get(1).time()).isEqualTo(0L);

        assertThat(speechMarks.get(2).type()).isEqualTo("word");
        assertThat(speechMarks.get(2).value()).isEqualTo("beta");
        assertThat(speechMarks.get(2).time()).isEqualTo(500L);
    }

    @Test
    @DisplayName("buildSpeechMarks calculates durations correctly for intermediate and final marks")
    void testDurationCalculation() {
        String text = "First sentence. Second sentence.";
        SsmlMarkBuilder.SsmlPlan plan = markBuilder.buildPlan(text);

        List<SsmlMarkBuilder.Timepoint> timepoints = List.of(
                new SsmlMarkBuilder.Timepoint("s_0", 0.0),
                new SsmlMarkBuilder.Timepoint("w_0", 0.0),
                new SsmlMarkBuilder.Timepoint("w_1", 0.4),
                new SsmlMarkBuilder.Timepoint("s_1", 1.0),
                new SsmlMarkBuilder.Timepoint("w_2", 1.0),
                new SsmlMarkBuilder.Timepoint("w_3", 1.5)
        );

        double totalDurationMs = 2200.0;
        List<SpeechDtos.SpeechMark> marks = markBuilder.buildSpeechMarks(plan, timepoints, totalDurationMs);

        // s_0 duration: next sentence is at 1000ms -> duration = 1000 - 0 = 1000ms
        SpeechDtos.SpeechMark s0 = marks.stream().filter(m -> "s_0".equals(findMarkId(plan.sentences(), m))).findFirst().orElseThrow();
        assertThat(s0.duration()).isEqualTo(1000L);

        // s_1 duration: last sentence, total is 2200ms -> duration = 2200 - 1000 = 1200ms
        SpeechDtos.SpeechMark s1 = marks.stream().filter(m -> "s_1".equals(findMarkId(plan.sentences(), m))).findFirst().orElseThrow();
        assertThat(s1.duration()).isEqualTo(1200L);

        // w_0 duration: next word is at 400ms -> duration = 400ms
        SpeechDtos.SpeechMark w0 = marks.stream().filter(m -> "w_0".equals(findMarkId(plan.words(), m))).findFirst().orElseThrow();
        assertThat(w0.duration()).isEqualTo(400L);

        // w_3 duration: last word at 1500ms -> duration = 2200 - 1500 = 700ms
        SpeechDtos.SpeechMark w3 = marks.stream().filter(m -> "w_3".equals(findMarkId(plan.words(), m))).findFirst().orElseThrow();
        assertThat(w3.duration()).isEqualTo(700L);
    }

    private String findMarkId(List<SsmlMarkBuilder.MarkToken> tokens, SpeechDtos.SpeechMark mark) {
        return tokens.stream()
                .filter(t -> t.start() == mark.start() && t.end() == mark.end() && t.value().equals(mark.value()))
                .map(SsmlMarkBuilder.MarkToken::id)
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("resolveGranularity resolves WORD_AND_SENTENCE, SENTENCE_ONLY, and NONE")
    void testResolveGranularity() {
        SpeechDtos.SpeechMark sentenceMark = new SpeechDtos.SpeechMark(0, 500, "sentence", 0, 10, "Hello text");
        SpeechDtos.SpeechMark wordMark = new SpeechDtos.SpeechMark(0, 250, "word", 0, 5, "Hello");

        // Both sentence and word marks present
        SpeechDtos.TimingGranularity full = markBuilder.resolveGranularity(List.of(sentenceMark, wordMark));
        assertThat(full).isEqualTo(SpeechDtos.TimingGranularity.WORD_AND_SENTENCE);

        // Only sentence mark present
        SpeechDtos.TimingGranularity sentenceOnly = markBuilder.resolveGranularity(List.of(sentenceMark));
        assertThat(sentenceOnly).isEqualTo(SpeechDtos.TimingGranularity.SENTENCE_ONLY);

        // Only word mark present
        SpeechDtos.TimingGranularity wordOnly = markBuilder.resolveGranularity(List.of(wordMark));
        assertThat(wordOnly).isEqualTo(SpeechDtos.TimingGranularity.NONE);

        // Empty list
        SpeechDtos.TimingGranularity empty = markBuilder.resolveGranularity(List.of());
        assertThat(empty).isEqualTo(SpeechDtos.TimingGranularity.NONE);

        // Null list
        SpeechDtos.TimingGranularity nullList = markBuilder.resolveGranularity(null);
        assertThat(nullList).isEqualTo(SpeechDtos.TimingGranularity.NONE);
    }

    @Test
    @DisplayName("Edge cases: null, empty string, and whitespace input")
    void testEdgeCases() {
        SsmlMarkBuilder.SsmlPlan nullPlan = markBuilder.buildPlan(null);
        assertThat(nullPlan.ssml()).isEqualTo("<speak></speak>");
        assertThat(nullPlan.sentences()).isEmpty();
        assertThat(nullPlan.words()).isEmpty();

        SsmlMarkBuilder.SsmlPlan emptyPlan = markBuilder.buildPlan("");
        assertThat(emptyPlan.ssml()).isEqualTo("<speak></speak>");
        assertThat(emptyPlan.sentences()).isEmpty();
        assertThat(emptyPlan.words()).isEmpty();

        SsmlMarkBuilder.SsmlPlan whitespacePlan = markBuilder.buildPlan("   \n\t  ");
        assertThat(whitespacePlan.sentences()).isEmpty();
        assertThat(whitespacePlan.words()).isEmpty();

        List<SpeechDtos.SpeechMark> emptyMarks = markBuilder.buildSpeechMarks(emptyPlan, List.of(), 1000.0);
        assertThat(emptyMarks).isEmpty();
    }
}
