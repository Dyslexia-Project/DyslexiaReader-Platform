package com.example.dyslexiaapp.speech;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SsmlMarkBuilder {

    public record MarkToken(
            String id,
            String type,
            int start,
            int end,
            String value
    ) {}

    public record SsmlPlan(
            String ssml,
            List<MarkToken> sentences,
            List<MarkToken> words
    ) {}

    public record Timepoint(
            String markName,
            double timeSeconds
    ) {}

    /**
     * Builds an SSML plan containing the escaped SSML with inserted &lt;mark&gt; tags
     * and token lists with exact UTF-16 code unit offsets measured against raw input text.
     */
    public SsmlPlan buildPlan(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new SsmlPlan("<speak></speak>", List.of(), List.of());
        }

        List<MarkToken> sentences = extractSentences(rawText);
        List<MarkToken> words = extractWords(rawText, sentences);

        // Map start indices to list of mark tags to emit before that character index
        Map<Integer, List<String>> marksAtIndex = new HashMap<>();
        for (MarkToken s : sentences) {
            marksAtIndex.computeIfAbsent(s.start(), _k -> new ArrayList<>()).add("<mark name=\"" + s.id() + "\"/>");
        }
        for (MarkToken w : words) {
            marksAtIndex.computeIfAbsent(w.start(), _k -> new ArrayList<>()).add("<mark name=\"" + w.id() + "\"/>");
        }

        StringBuilder ssml = new StringBuilder();
        ssml.append("<speak>");

        for (int k = 0; k < rawText.length(); k++) {
            List<String> marks = marksAtIndex.get(k);
            if (marks != null) {
                for (String markTag : marks) {
                    ssml.append(markTag);
                }
            }

            char c = rawText.charAt(k);
            switch (c) {
                case '&' -> ssml.append("&amp;");
                case '<' -> ssml.append("&lt;");
                case '>' -> ssml.append("&gt;");
                case '"' -> ssml.append("&quot;");
                case '\'' -> ssml.append("&apos;");
                default -> ssml.append(c);
            }
        }

        // Check if any mark points to the end of string (e.g. edge cases)
        List<String> endMarks = marksAtIndex.get(rawText.length());
        if (endMarks != null) {
            for (String markTag : endMarks) {
                ssml.append(markTag);
            }
        }

        ssml.append("</speak>");

        return new SsmlPlan(ssml.toString(), List.copyOf(sentences), List.copyOf(words));
    }

    public SsmlPlan buildSsmlPlan(String rawText) {
        return buildPlan(rawText);
    }

    private List<MarkToken> extractSentences(String text) {
        List<MarkToken> sentences = new ArrayList<>();
        int n = text.length();
        int i = 0;
        int sentenceIndex = 0;

        while (i < n) {
            // Skip leading whitespace
            while (i < n && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= n) break;

            int start = i;
            int end = i;

            while (i < n) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    end = i;
                    while (i < n && (text.charAt(i) == '\n' || text.charAt(i) == '\r' || Character.isWhitespace(text.charAt(i)))) {
                        if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                            i++;
                        } else {
                            break;
                        }
                    }
                    break;
                } else if (c == '.' || c == '!' || c == '?') {
                    i++;
                    while (i < n && (text.charAt(i) == '.' || text.charAt(i) == '!' || text.charAt(i) == '?')) {
                        i++;
                    }
                    while (i < n && (text.charAt(i) == '"' || text.charAt(i) == '\'' || text.charAt(i) == '”' || text.charAt(i) == '’' || text.charAt(i) == ')')) {
                        i++;
                    }
                    end = i;
                    break;
                } else {
                    i++;
                    end = i;
                }
            }

            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }

            if (end > start) {
                sentences.add(new MarkToken(
                        "s_" + sentenceIndex++,
                        "sentence",
                        start,
                        end,
                        text.substring(start, end)
                ));
            }
        }

        return sentences;
    }

    private List<MarkToken> extractWords(String text, List<MarkToken> sentences) {
        List<MarkToken> words = new ArrayList<>();
        int wordIndex = 0;

        for (MarkToken sentence : sentences) {
            int sStart = sentence.start();
            int sEnd = sentence.end();
            int j = sStart;

            while (j < sEnd) {
                // Advance until letter or digit
                while (j < sEnd && !Character.isLetterOrDigit(text.codePointAt(j))) {
                    j += Character.charCount(text.codePointAt(j));
                }
                if (j >= sEnd) break;

                int wStart = j;
                int wEnd = j;

                while (j < sEnd) {
                    int cp = text.codePointAt(j);
                    if (Character.isLetterOrDigit(cp)) {
                        j += Character.charCount(cp);
                        wEnd = j;
                    } else if (cp == '\'' || cp == '\u2019' || cp == '-') {
                        // Apostrophe or hyphen allowed only if followed immediately by letter/digit
                        int nextIdx = j + Character.charCount(cp);
                        if (nextIdx < sEnd && Character.isLetterOrDigit(text.codePointAt(nextIdx))) {
                            j = nextIdx;
                            wEnd = j;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }

                if (wEnd > wStart) {
                    words.add(new MarkToken(
                            "w_" + wordIndex++,
                            "word",
                            wStart,
                            wEnd,
                            text.substring(wStart, wEnd)
                    ));
                }
            }
        }

        return words;
    }

    /**
     * Converts Google Cloud SSML timepoints to millisecond SpeechMark records.
     */
    public List<SpeechDtos.SpeechMark> buildSpeechMarks(SsmlPlan plan, List<Timepoint> timepoints, double totalEstimatedDurationMs) {
        if (plan == null || timepoints == null || timepoints.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> markTimes = new HashMap<>();
        for (Timepoint tp : timepoints) {
            if (tp != null && tp.markName() != null) {
                markTimes.put(tp.markName(), Math.round(tp.timeSeconds() * 1000.0));
            }
        }

        List<SpeechDtos.SpeechMark> sentenceMarks = new ArrayList<>();
        List<MarkToken> sentencesWithTime = new ArrayList<>();
        for (MarkToken s : plan.sentences()) {
            if (!markTimes.containsKey(s.id())) {
                // Google Cloud TTS collapses consecutive <mark name="s_i"/><mark name="w_j"/> tags;
                // inherit the timestamp of the first word in this sentence when s_i is omitted.
                for (MarkToken w : plan.words()) {
                    if (w.start() >= s.start() && w.start() < s.end() && markTimes.containsKey(w.id())) {
                        markTimes.put(s.id(), markTimes.get(w.id()));
                        break;
                    }
                }
            }
            if (markTimes.containsKey(s.id())) {
                sentencesWithTime.add(s);
            }
        }

        for (int i = 0; i < sentencesWithTime.size(); i++) {
            MarkToken token = sentencesWithTime.get(i);
            long time = markTimes.get(token.id());
            long duration;
            if (i + 1 < sentencesWithTime.size()) {
                long nextTime = markTimes.get(sentencesWithTime.get(i + 1).id());
                duration = Math.max(0, nextTime - time);
            } else {
                long total = Math.round(totalEstimatedDurationMs);
                duration = (total > time) ? (total - time) : 1000L;
            }
            sentenceMarks.add(new SpeechDtos.SpeechMark(time, duration, "sentence", token.start(), token.end(), token.value()));
        }

        List<SpeechDtos.SpeechMark> wordMarks = new ArrayList<>();
        List<MarkToken> wordsWithTime = new ArrayList<>();
        for (MarkToken w : plan.words()) {
            if (markTimes.containsKey(w.id())) {
                wordsWithTime.add(w);
            }
        }

        for (int i = 0; i < wordsWithTime.size(); i++) {
            MarkToken token = wordsWithTime.get(i);
            long time = markTimes.get(token.id());
            long duration;
            if (i + 1 < wordsWithTime.size()) {
                long nextTime = markTimes.get(wordsWithTime.get(i + 1).id());
                duration = Math.max(0, nextTime - time);
            } else {
                long total = Math.round(totalEstimatedDurationMs);
                duration = (total > time) ? (total - time) : 300L;
            }
            wordMarks.add(new SpeechDtos.SpeechMark(time, duration, "word", token.start(), token.end(), token.value()));
        }

        List<SpeechDtos.SpeechMark> combined = new ArrayList<>(sentenceMarks.size() + wordMarks.size());
        combined.addAll(sentenceMarks);
        combined.addAll(wordMarks);

        combined.sort((a, b) -> {
            int cmp = Long.compare(a.time(), b.time());
            if (cmp != 0) return cmp;
            boolean aIsSentence = "sentence".equalsIgnoreCase(a.type());
            boolean bIsSentence = "sentence".equalsIgnoreCase(b.type());
            if (aIsSentence != bIsSentence) {
                return aIsSentence ? -1 : 1;
            }
            return Integer.compare(a.start(), b.start());
        });

        return combined;
    }

    /**
     * Resolves the TimingGranularity according to the presence of word and sentence marks.
     */
    public SpeechDtos.TimingGranularity resolveGranularity(List<SpeechDtos.SpeechMark> marks) {
        if (marks == null || marks.isEmpty()) {
            return SpeechDtos.TimingGranularity.NONE;
        }
        boolean hasSentence = false;
        boolean hasWord = false;
        for (SpeechDtos.SpeechMark mark : marks) {
            if ("sentence".equalsIgnoreCase(mark.type())) {
                hasSentence = true;
            } else if ("word".equalsIgnoreCase(mark.type())) {
                hasWord = true;
            }
        }
        if (hasSentence && hasWord) {
            return SpeechDtos.TimingGranularity.WORD_AND_SENTENCE;
        }
        if (hasSentence) {
            return SpeechDtos.TimingGranularity.SENTENCE_ONLY;
        }
        return SpeechDtos.TimingGranularity.NONE;
    }
}
