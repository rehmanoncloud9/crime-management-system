package com.cms.model.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * Transient state of a conversation, tracking intents and extracted entities (slots).
 */
public class DialogueState {
    private String lastIntent;
    private final Map<String, String> slots = new HashMap<>();
    private boolean awaitingInput = false;
    private String lastQuestion;

    public String getLastIntent() { return lastIntent; }
    public void setLastIntent(String lastIntent) { this.lastIntent = lastIntent; }

    public void setSlot(String key, String value) { slots.put(key, value); }
    public String getSlot(String key) { return slots.get(key); }
    public Map<String, String> getSlots() { return slots; }

    public boolean isAwaitingInput() { return awaitingInput; }
    public void setAwaitingInput(boolean awaitingInput) { this.awaitingInput = awaitingInput; }

    public String getLastQuestion() { return lastQuestion; }
    public void setLastQuestion(String lastQuestion) { this.lastQuestion = lastQuestion; }

    public void reset() {
        lastIntent = null;
        slots.clear();
        awaitingInput = false;
        lastQuestion = null;
    }
}
