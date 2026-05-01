package com.cms.service.ai;

import com.cms.model.ai.DialogueState;
import com.cms.model.ai.ParsedQuery;
import java.util.*;
import java.util.regex.*;

/**
 * Manages conversation flow, multi-turn state, and action confirmations.
 */
public class DialogueManager {
    private final Map<String, DialogueState> sessions = new HashMap<>();
    private final Map<String, ParsedQuery> pendingUpdates = new HashMap<>(); // Also used for confirmation of creation/delete
    private final Map<String, Boolean> awaitingConfirmation = new HashMap<>();
    
    // Creation State Tracking
    private final Map<String, Boolean> awaitingCreation = new HashMap<>();
    private final Map<String, Map<String, Object>> creationFields = new HashMap<>();
    private final Map<String, String> creationType = new HashMap<>();
    private final Map<String, List<String>> requiredFields = new HashMap<>();

    public DialogueState getState(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new DialogueState());
    }

    public void updateState(String sessionId, NLPEngine.IntentResult nlpResult) {
        DialogueState state = getState(sessionId);
        if (state.isAwaitingInput()) {
            if (!nlpResult.entities.isEmpty()) {
                state.setSlot("pending_value", nlpResult.entities.get(0));
            }
            state.setAwaitingInput(false);
        } else {
            state.setLastIntent(nlpResult.intent);
            if (!nlpResult.entities.isEmpty()) {
                state.setSlot("primary_entity", nlpResult.entities.get(0));
            }
        }
    }

    // --- Confirmation Logic ---
    public boolean isAwaitingConfirmation(String sessionId) {
        return awaitingConfirmation.getOrDefault(sessionId, false);
    }

    public void setPendingUpdate(String sessionId, ParsedQuery pq) {
        pendingUpdates.put(sessionId, pq);
        awaitingConfirmation.put(sessionId, true);
    }

    public ParsedQuery getPendingUpdate(String sessionId) {
        return pendingUpdates.get(sessionId);
    }

    public void clearPendingUpdate(String sessionId) {
        pendingUpdates.remove(sessionId);
        awaitingConfirmation.put(sessionId, false);
    }

    public String getFollowUpQuestion(String sessionId) {
        DialogueState state = getState(sessionId);
        if ("SEARCH".equals(state.getLastIntent()) && state.getSlot("primary_entity") == null) {
            state.setAwaitingInput(true);
            state.setLastQuestion("Who should I search for?");
            return state.getLastQuestion();
        }
        return null;
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        pendingUpdates.remove(sessionId);
        awaitingConfirmation.remove(sessionId);
        awaitingCreation.remove(sessionId);
        creationFields.remove(sessionId);
        creationType.remove(sessionId);
        requiredFields.remove(sessionId);
    }

    // --- Creation Flow Logic ---
    public boolean isAwaitingCreation(String sessionId) {
        return awaitingCreation.getOrDefault(sessionId, false);
    }

    public void startCreation(String sessionId, String type, List<String> fields) {
        creationType.put(sessionId, type);
        requiredFields.put(sessionId, fields);
        creationFields.put(sessionId, new HashMap<>());
        awaitingCreation.put(sessionId, true);
    }

    public void addCreationField(String sessionId, String field, Object value) {
        creationFields.computeIfAbsent(sessionId, k -> new HashMap<>()).put(field, value);
    }

    public Map<String, Object> getCreationFields(String sessionId) {
        return creationFields.getOrDefault(sessionId, new HashMap<>());
    }

    public String getCreationType(String sessionId) {
        return creationType.get(sessionId);
    }

    public List<String> getRemainingFields(String sessionId) {
        List<String> required = requiredFields.get(sessionId);
        if (required == null) return Collections.emptyList();
        
        List<String> remaining = new ArrayList<>(required);
        Map<String, Object> collected = creationFields.get(sessionId);
        if (collected != null) {
            remaining.removeAll(collected.keySet());
        }
        return remaining;
    }

    public void clearCreation(String sessionId) {
        awaitingCreation.remove(sessionId);
        creationFields.remove(sessionId);
        creationType.remove(sessionId);
        requiredFields.remove(sessionId);
    }
}
