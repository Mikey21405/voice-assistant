package org.example.voice_assistant.tts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accumulates streaming text tokens and detects complete sentences
 * based on sentence-ending punctuation.
 */
public class SentenceBoundaryDetector {

    private static final Pattern SENTENCE_END = Pattern.compile("[。！？,.!?]");

    private final StringBuilder buffer = new StringBuilder();

    /**
     * Feed a token. Returns a complete sentence if one is detected, null otherwise.
     * The returned sentence includes the ending punctuation and any text before it.
     */
    public String feed(String token) {
        buffer.append(token);
        String current = buffer.toString();

        Matcher m = SENTENCE_END.matcher(current);
        int lastEnd = -1;
        while (m.find()) {
            lastEnd = m.end();
        }

        if (lastEnd > 0) {
            String sentence = current.substring(0, lastEnd);
            String remaining = current.substring(lastEnd);
            buffer.setLength(0);
            buffer.append(remaining);
            return sentence.trim();
        }

        return null;
    }

    /**
     * Flush any remaining buffered text that didn't end with a boundary punctuation.
     * Returns the remaining text, or null if the buffer is empty.
     */
    public String flush() {
        String remaining = buffer.toString().trim();
        buffer.setLength(0);
        return remaining.isEmpty() ? null : remaining;
    }
}
