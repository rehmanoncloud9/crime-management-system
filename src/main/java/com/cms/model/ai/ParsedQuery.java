package com.cms.model.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for information extracted from user natural language input.
 */
public class ParsedQuery {
    private String intent;          // SEARCH, UPDATE, ANALYZE, STATS, HELP, UNKNOWN
    private String entityType;      // CRIMINAL, CASE, OFFICER, EVIDENCE
    private Map<String, Object> attributes = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>(); // for query binding
    private boolean isMultiStep = false;
    private String pendingField;

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public boolean hasAttribute(String key) { return attributes.containsKey(key); }
    public Object getAttribute(String key) { return attributes.get(key); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameter(String key, Object value) { parameters.put(key, value); }
    
    public boolean isMultiStep() { return isMultiStep; }
    public void setMultiStep(boolean multiStep) { isMultiStep = multiStep; }

    public String getPendingField() { return pendingField; }
    public void setPendingField(String pendingField) { this.pendingField = pendingField; }
}
