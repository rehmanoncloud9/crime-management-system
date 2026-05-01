package com.cms.service.ai;

import com.cms.model.ai.ParsedQuery;
import java.util.*;
import java.util.regex.*;

/**
 * Intelligent Query Parser using Regex and Synonym Mapping.
 * Phase 9 Update: Added COUNT, CREATE, and LIST intents.
 */
public class QueryParser {

    private static final Map<String, String> INTENT_PATTERNS = new HashMap<>();
    static {
        INTENT_PATTERNS.put("^.*(how many|count|total).*(persons|criminals|suspects|cases|files|incidents|officers).*$", "COUNT");
        INTENT_PATTERNS.put("(?i)^.*(create|make|add|new).*(case|person|criminal|officer|incident).*$", "CREATE");
        INTENT_PATTERNS.put("^.*(list|show all).*(crime types|categories|offices|cases).*$", "LIST");
    }

    // Conversational Patterns
    private static final Pattern GREETING_PATTERN = Pattern.compile(
        "^(hi|hello|hey|good morning|good afternoon|good evening|howdy).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern THANKS_PATTERN = Pattern.compile(
        ".*(thank|thanks|appreciate).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYE_PATTERN = Pattern.compile(
        "(bye|goodbye|exit|quit|see you).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_CHECK_PATTERN = Pattern.compile(
        "(how are you|how's it going|how do you do).*", Pattern.CASE_INSENSITIVE);

    // Intent Patterns
    private static final Pattern DEFINE_CRIME_PATTERN = Pattern.compile(
        ".*(what is|define|explain|tell me about)\\s+([a-zA-Z\\s]+)", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern SEARCH_CRIMINAL_PATTERN = Pattern.compile(
        ".*(search|find|show|look for)\\s+(suspects?|criminals?|persons?|people)\\s+(in|named|with|at)?\\s*(.*)", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern SEARCH_CASE_PATTERN = Pattern.compile(
        ".*(search|find|show|look for)\\s+(cases?|files?|incidents?)\\s+(#|id|named|type)?\\s*(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern UPDATE_STATUS_PATTERN = Pattern.compile(
        ".*update\\s+status\\s+of\\s+([a-zA-Z\\s]+)\\s+to\\s+([a-zA-Z\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANALYZE_PATTERN = Pattern.compile(
        ".*(analyze|investigate|examine)\\s+case\\s+(#|id)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public ParsedQuery parse(String input) {
        ParsedQuery pq = new ParsedQuery();
        String lower = input.toLowerCase();

        // 1. Check complex regex-based intents first
        for (Map.Entry<String, String> entry : INTENT_PATTERNS.entrySet()) {
            if (lower.matches(entry.getKey())) {
                pq.setIntent(entry.getValue());
                extractOperationalAttributes(pq, lower);
                return pq;
            }
        }

        // 2. Conversational First
        if (GREETING_PATTERN.matcher(lower).matches()) {
            pq.setIntent("GREETING");
            return pq;
        }
        if (THANKS_PATTERN.matcher(lower).matches()) {
            pq.setIntent("THANKS");
            return pq;
        }
        if (BYE_PATTERN.matcher(lower).matches()) {
            pq.setIntent("BYE");
            return pq;
        }
        if (STATUS_CHECK_PATTERN.matcher(lower).matches()) {
            pq.setIntent("HOW_ARE_YOU");
            return pq;
        }

        // 3. Specialized Definitions
        Matcher defineMatcher = DEFINE_CRIME_PATTERN.matcher(lower);
        if (defineMatcher.find()) {
            pq.setIntent("DEFINE_CRIME");
            pq.setAttribute("searchTerm", defineMatcher.group(2).trim());
            return pq;
        }

        if (lower.contains("system statistics") || lower.contains("stats")) {
            pq.setIntent("STATS");
            return pq;
        }

        if (lower.equals("help") || lower.contains("commands")) {
            pq.setIntent("HELP");
            return pq;
        }

        // 4. Data Queries
        Matcher updateMatcher = UPDATE_STATUS_PATTERN.matcher(lower);
        if (updateMatcher.find()) {
            pq.setIntent("UPDATE");
            pq.setAttribute("targetName", updateMatcher.group(1).trim());
            pq.setAttribute("updateAttribute", "status");
            pq.setAttribute("updateValue", updateMatcher.group(2).trim());
            return pq;
        }

        Matcher analyzeMatcher = ANALYZE_PATTERN.matcher(lower);
        if (analyzeMatcher.find()) {
            pq.setIntent("ANALYZE");
            pq.setAttribute("caseId", Long.parseLong(analyzeMatcher.group(3)));
            return pq;
        }

        Matcher criminalMatcher = SEARCH_CRIMINAL_PATTERN.matcher(lower);
        if (criminalMatcher.find()) {
            pq.setIntent("SEARCH");
            pq.setEntityType("CRIMINAL");
            extractSearchAttributes(pq, criminalMatcher.group(4));
            return pq;
        }

        Matcher caseMatcher = SEARCH_CASE_PATTERN.matcher(lower);
        if (caseMatcher.find()) {
            pq.setIntent("SEARCH");
            pq.setEntityType("CASE");
            extractSearchAttributes(pq, caseMatcher.group(4));
            return pq;
        }

        pq.setIntent("UNKNOWN");
        return pq;
    }

    private void extractOperationalAttributes(ParsedQuery pq, String lower) {
        // Extract Entity Type
        if (lower.contains("person") || lower.contains("criminal") || lower.contains("suspect")) {
            pq.setEntityType("PERSON");
        } else if (lower.contains("case") || lower.contains("file") || lower.contains("incident")) {
            pq.setEntityType("CASE");
        } else if (lower.contains("officer") || lower.contains("investigator")) {
            pq.setEntityType("OFFICER");
        }

        // For COUNT and LIST, entity type is also the target
        if ("COUNT".equals(pq.getIntent()) || "LIST".equals(pq.getIntent())) {
            pq.setAttribute("target", pq.getEntityType());
        }

        // For CREATE, determine specific creation type
        if ("CREATE".equals(pq.getIntent())) {
            pq.setAttribute("createType", pq.getEntityType());
        }
    }

    private void extractSearchAttributes(ParsedQuery pq, String criteria) {
        if (criteria == null || criteria.isBlank()) return;

        // Simple attribute extraction
        if (criteria.contains("lahore")) pq.setAttribute("location", "Lahore");
        if (criteria.contains("karachi")) pq.setAttribute("location", "Karachi");
        if (criteria.contains("tattoo")) pq.setAttribute("hasTattoo", true);
        if (criteria.contains("scar")) pq.setAttribute("hasScar", true);
        
        // Crime types as attributes
        if (criteria.contains("robbery")) pq.setAttribute("crimeType", "Robbery");
        if (criteria.contains("theft")) pq.setAttribute("crimeType", "Theft");
        if (criteria.contains("murder")) pq.setAttribute("crimeType", "Murder");

        if ("CRIMINAL".equals(pq.getEntityType()) && !pq.hasAttribute("name")) {
             pq.setAttribute("name", criteria.trim());
        }
    }
}
