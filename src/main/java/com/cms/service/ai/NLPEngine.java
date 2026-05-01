package com.cms.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight NLP Engine using Regex and Keyword analysis for fallback
 * when heavy ML libraries are not available.
 */
public class NLPEngine {
    
    public static class IntentResult {
        public String intent;
        public List<String> entities = new ArrayList<>();
        
        public IntentResult(String intent) { this.intent = intent; }
    }

    public IntentResult process(String text) {
        String q = text.toLowerCase().trim();
        
        // --- 1. SEARCH INTENT ---
        if (q.contains("search") || q.contains("find") || q.contains("who is")) {
            IntentResult res = new IntentResult("SEARCH");
            // Extract potential names (capitalized words or specific patterns)
            extractName(text, res);
            return res;
        }

        // --- 2. ANALYZE INTENT ---
        if (q.contains("analyze case") || q.contains("drill down")) {
            IntentResult res = new IntentResult("ANALYZE");
            extractId(q, res);
            return res;
        }

        // --- 3. STATISTICS INTENT ---
        if (q.contains("statistic") || q.contains("how many") || q.contains("summary") || q.contains("overview")) {
            return new IntentResult("STATS");
        }

        // --- 4. GEOGRAPHIC INTENT ---
        if (q.contains("hotspot") || q.contains("district") || q.contains("dangerous city")) {
            return new IntentResult("HOTSPOTS");
        }

        // --- 5. PATTERN INTENT ---
        if (q.contains("pattern") || q.contains("modus operandi") || q.contains("mo analysis")) {
            return new IntentResult("PATTERNS");
        }

        // --- 6. HELP INTENT ---
        if (q.contains("help") || q.contains("what can you do")) {
            return new IntentResult("HELP");
        }

        return new IntentResult("UNKNOWN");
    }

    private void extractName(String rawText, IntentResult res) {
        // Simple heuristic: look for "search [name]" or "find [name]"
        Pattern p = Pattern.compile("(?:search|find|who is)\\s+([a-zA-Z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(rawText);
        if (m.find()) {
            res.entities.add(m.group(1).trim());
        }
    }

    private void extractId(String q, IntentResult res) {
        // Look for numbers which might be IDs
        Pattern p = Pattern.compile("\\b(\\d+)\\b");
        Matcher m = p.matcher(q);
        while (m.find()) {
            res.entities.add(m.group(1));
        }
    }
}
