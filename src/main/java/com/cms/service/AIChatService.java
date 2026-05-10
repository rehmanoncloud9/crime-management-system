package com.cms.service;

import com.cms.model.*;
import com.cms.model.ai.ParsedQuery;
import com.cms.model.enums.*;
import com.cms.service.ai.*;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Phase 7++: Intelligent Query & Dynamic Discovery Overhaul.
 * Separates NLU (Parser), Business Logic (Dialogue), and Data (Builder).
 */
public class AIChatService {
    private static final Logger logger = LoggerFactory.getLogger(AIChatService.class);

    private final QueryParser parser = new QueryParser();
    private final DialogueManager dialogueManager = new DialogueManager();

    public String processQuery(String query, String sessionId) {
        if (query == null || query.isBlank()) return "Please enter a command.";

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            saveMessage(sessionId, "USER", query, session);

            // 1. Handle STATEFUL Flows First
            if (dialogueManager.isAwaitingConfirmation(sessionId)) {
                return handleConfirmation(query, sessionId, session);
            }
            if (dialogueManager.isAwaitingCreation(sessionId)) {
                return handleCreation(query, sessionId, session);
            }

            // 2. Intelligent Parsing
            ParsedQuery pq = parser.parse(query);

            // 3. Execution based on Intent
            String response = switch (pq.getIntent()) {
                case "GREETING" -> "Hello! I'm your CMS Intelligence Assistant. How can I help with your investigation today?";
                case "THANKS" -> "You're welcome! I'm here to help. Any other records you'd like me to look up?";
                case "BYE" -> "Goodbye! Stay safe and stay sharp on the field.";
                case "HOW_ARE_YOU" -> "I'm fully operational and synchronized with the latest case files. How can I assist you?";
                case "HELP" -> getHelpText();
                case "DEFINE_CRIME" -> handleDefineCrime(pq);
                case "COUNT" -> handleCount(pq, session);
                case "LIST" -> handleList(pq, session);
                case "SEARCH" -> handleSearch(pq, session);
                case "CREATE" -> handleCreate(pq, sessionId);
                case "UPDATE" -> handleUpdateIntent(pq, sessionId);
                case "ANALYZE" -> handleAnalyze(pq, session);
                case "STATS" -> handleStats(session);
                default -> "🤖 I don't recognize that command. Type 'help' for examples or ask 'what is robbery?'.";
            };

            saveMessage(sessionId, "AI", response, session);
            return response;

        } catch (Exception e) {
            logger.error("AI processing error", e);
            return "❌ Error: " + e.getMessage();
        }
    }

    private String handleDefineCrime(ParsedQuery pq) {
        String term = (String) pq.getAttribute("searchTerm");
        CrimeTypeService crimeService = new CrimeTypeService();
        CrimeType ct = crimeService.findCrimeType(term);
        if (ct == null) return "I couldn't find detailed information about '" + term + "' in our legal database.";
        return crimeService.getCrimeTypeInfo(ct);
    }

    private String handleCount(ParsedQuery pq, Session session) {
        String target = (String) pq.getAttribute("target");
        if (target == null) return "What would you like to count? (e.g., persons, cases, officers)";

        long count = 0;
        switch (target) {
            case "PERSON" -> count = session.createQuery("SELECT COUNT(p) FROM Person p", Long.class).uniqueResult();
            case "CASE" -> count = session.createQuery("SELECT COUNT(cf) FROM CaseFile cf", Long.class).uniqueResult();
            case "OFFICER" -> count = session.createQuery("SELECT COUNT(u) FROM User u", Long.class).uniqueResult();
            default -> { return "I can only count persons, cases, or officers."; }
        }
        return "📊 Total " + target.toLowerCase() + "s: **" + count + "**";
    }

    private String handleList(ParsedQuery pq, Session session) {
        String target = (String) pq.getAttribute("target");
        if ("CRIME_TYPE".equals(target) || "crime types".equals(pq.getAttribute("searchTerm"))) {
            List<CrimeType> crimes = new CrimeTypeService().findAll();
            if (crimes.isEmpty()) return "No crime types found.";
            StringBuilder sb = new StringBuilder("📜 **Crime Categories:**\n");
            crimes.forEach(c -> sb.append("- ").append(c.getName()).append(" (").append(c.getCode()).append(")\n"));
            return sb.toString();
        }
        return "I can list 'crime types'. What else would you like to see?";
    }

    // ================= CREATE (Multi-Step) =================
    private String handleCreate(ParsedQuery pq, String sessionId) {
        String type = (String) pq.getAttribute("createType");
        if (type == null) return "What would you like to create? (e.g., 'make a new case', 'add a criminal')";

        List<String> required;
        if ("CASE".equals(type) || "INCIDENT".equals(type)) {
            required = Arrays.asList("title", "crimeType", "location", "description");
        } else if ("PERSON".equals(type)) {
            required = Arrays.asList("firstName", "lastName", "status");
        } else {
            return "I can currently help you create 'cases' or 'criminals'.";
        }

        dialogueManager.startCreation(sessionId, type, required);
        return "✨ Let's create a new **" + type.toLowerCase() + "**. \nWhat is the **" + required.get(0) + "**?";
    }

    private String handleCreation(String input, String sessionId, Session session) {
        String type = dialogueManager.getCreationType(sessionId);
        List<String> remaining = dialogueManager.getRemainingFields(sessionId);
        
        if (remaining.isEmpty()) {
            return "Creation state error. Please restart.";
        }

        String currentField = remaining.get(0);
        dialogueManager.addCreationField(sessionId, currentField, input.trim());
        
        List<String> nextRemaining = dialogueManager.getRemainingFields(sessionId);
        if (nextRemaining.isEmpty()) {
            // All collected -> confirmation
            Map<String, Object> fields = dialogueManager.getCreationFields(sessionId);
            StringBuilder sb = new StringBuilder("✅ **Ready to create " + type.toLowerCase() + ":**\n");
            fields.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            
            ParsedQuery pending = new ParsedQuery();
            pending.setIntent("CREATE_FINAL");
            pending.setEntityType(type);
            fields.forEach(pending::setAttribute);
            
            dialogueManager.setPendingUpdate(sessionId, pending);
            dialogueManager.clearCreation(sessionId);
            return sb.toString() + "\n**Confirm creation? (yes/no)**";
        } else {
            return "Got it. What is the **" + nextRemaining.get(0) + "**?";
        }
    }

    private String handleSearch(ParsedQuery pq, Session session) {
        QueryBuilder builder = new QueryBuilder(session);
        List<Object> results;
        
        if ("CASE".equals(pq.getEntityType())) {
            results = builder.buildCaseSearchQuery(pq).getResultList();
        } else {
            results = builder.buildCriminalSearchQuery(pq).getResultList();
        }

        if (results.isEmpty()) return "No matching records found.";

        StringBuilder sb = new StringBuilder("🔍 Discovery Results:\n\n");
        for (Object obj : results) {
            if (obj instanceof Person p) {
                sb.append("• ").append(p.getFirstName()).append(" ").append(p.getLastName())
                  .append(" (").append(p.getPersonStatus()).append(") at ").append(p.getAddress()).append("\n");
            } else if (obj instanceof CaseFile cf) {
                sb.append("• Case #").append(cf.getCaseNumber()).append(": ").append(cf.getIncident().getTitle()).append("\n");
            }
        }
        return sb.toString();
    }

    private String handleUpdateIntent(ParsedQuery pq, String sessionId) {
        if (!pq.hasAttribute("targetName") || !pq.hasAttribute("updateValue")) {
            return "Specify who to update and the new value. e.g., 'update status of Ali to Arrested'";
        }
        dialogueManager.setPendingUpdate(sessionId, pq);
        return "⚠️ Are you sure you want to change " + pq.getAttribute("targetName") + "'s " +
               pq.getAttribute("updateAttribute") + " to " + pq.getAttribute("updateValue") + "? (yes/no)";
    }

    private String handleConfirmation(String input, String sessionId, Session session) {
        if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y")) {
            ParsedQuery pending = dialogueManager.getPendingUpdate(sessionId);
            String response;
            
            if ("CREATE_FINAL".equals(pending.getIntent())) {
                response = performCreate(pending, session);
            } else {
                response = performUpdate(pending, session);
            }
            
            dialogueManager.clearPendingUpdate(sessionId);
            return response;
        } else {
            dialogueManager.clearPendingUpdate(sessionId);
            return "🚫 Action cancelled.";
        }
    }

    private String performCreate(ParsedQuery pq, Session session) {
        try {
            session.beginTransaction();
            String type = pq.getEntityType();
            if ("CASE".equals(type)) {
                CrimeIncident incident = new CrimeIncident();
                incident.setTitle((String) pq.getAttribute("title"));
                incident.setLocationAddress((String) pq.getAttribute("location"));
                incident.setDescription((String) pq.getAttribute("description"));
                incident.setIncidentNumber("INC-" + System.currentTimeMillis());
                
                // Lookup CrimeType
                String ctName = (String) pq.getAttribute("crimeType");
                CrimeType ct = new CrimeTypeService().findCrimeType(ctName);
                if (ct == null) {
                    session.getTransaction().rollback();
                    return "❌ Crime type '" + ctName + "' not found. Please specify a valid crime type.";
                }
                incident.setCrimeType(ct);
                incident.setReportingOfficer(SessionManager.getInstance().getCurrentUser());
                incident.setOccurredAt(LocalDateTime.now()); // Set mandatory field
                session.persist(incident);
                
                CaseFile cf = new CaseFile();
                cf.setCaseNumber("CASE-" + System.currentTimeMillis());
                cf.setIncident(incident);
                cf.setStatus(com.cms.model.enums.CaseStatus.OPEN);
                session.persist(cf);
                
                session.getTransaction().commit();
                return "🎉 Case **" + cf.getCaseNumber() + "** created successfully based on the incident!";
            } else if ("PERSON".equals(type)) {
                Person p = new Person();
                p.setFirstName((String) pq.getAttribute("firstName"));
                p.setLastName((String) pq.getAttribute("lastName"));
                try {
                    PersonStatus status = PersonStatus.valueOf(((String) pq.getAttribute("status")).toUpperCase());
                    p.setPersonStatus(status);
                } catch (Exception ex) {
                    session.getTransaction().rollback();
                    return "❌ Invalid status. Try: SUSPECT, WITNESS, VICTIM, CRIMINAL, IN_CUSTODY.";
                }
                session.persist(p);
                session.getTransaction().commit();
                return "👤 Person **" + p.getFirstName() + " " + p.getLastName() + "** added to database.";
            }
            return "Unsupported entity type for creation.";
        } catch (Exception e) {
            if (session.getTransaction().isActive()) session.getTransaction().rollback();
            return "❌ Creation failed: " + e.getMessage();
        }
    }

    private String performUpdate(ParsedQuery update, Session session) {
        String target = (String) update.getAttribute("targetName");
        String attr = (String) update.getAttribute("updateAttribute");
        String val = (String) update.getAttribute("updateValue");

        try {
            session.beginTransaction();
            List<Person> list = session.createQuery("FROM Person WHERE firstName LIKE :name OR lastName LIKE :name", Person.class)
                    .setParameter("name", "%" + target + "%").list();
            
            if (list.isEmpty()) {
                session.getTransaction().rollback();
                return "Person '" + target + "' not found.";
            }
            Person p = list.get(0);
            
            if ("status".equalsIgnoreCase(attr)) {
                p.setPersonStatus(PersonStatus.valueOf(val.toUpperCase()));
            }
            session.merge(p);
            session.getTransaction().commit();
            return "✅ Successfully updated " + target + "'s " + attr + " to " + val + ".";
        } catch (Exception e) {
            if (session.getTransaction().isActive()) session.getTransaction().rollback();
            return "❌ Update failed: " + e.getMessage();
        }
    }

    private String handleAnalyze(ParsedQuery pq, Session session) {
        if (!pq.hasAttribute("caseId")) return "Specify a Case ID to analyze (e.g., 'analyze case 1').";
        
        Long id = (Long) pq.getAttribute("caseId");
        CaseFile cf = session.get(CaseFile.class, id);
        if (cf == null) return "Case ID " + id + " not found.";

        SimilarityEngine sim = new SimilarityEngine(session);
        List<CaseFile> similar = sim.findSimilarCases(cf);

        StringBuilder sb = new StringBuilder("📁 Analysis for Case #").append(cf.getCaseNumber()).append("\n\n");
        sb.append("Title: ").append(cf.getIncident().getTitle()).append("\n");
        sb.append("Type: ").append(cf.getIncident().getCrimeType().getName()).append("\n\n");
        
        if (!similar.isEmpty()) {
            sb.append("🔗 Similar Cases Found:\n");
            similar.forEach(s -> sb.append("- #").append(s.getCaseNumber()).append(": ").append(s.getIncident().getTitle()).append("\n"));
        } else {
            sb.append("No similar patterns detected.");
        }
        return sb.toString();
    }

    private String handleStats(Session session) {
        Long counts = session.createQuery("SELECT COUNT(p) FROM Person p", Long.class).uniqueResult();
        Long cases = session.createQuery("SELECT COUNT(cf) FROM CaseFile cf", Long.class).uniqueResult();
        return "📊 System Overview:\n- Total Registered Persons: " + counts + "\n- Active Case Files: " + cases;
    }

    private String getHelpText() {
        return "🤖 Intelligence Commands:\n" +
               "- 'search suspects in Lahore with tattoos'\n" +
               "- 'show all robbery cases'\n" +
               "- 'update status of Ali to Arrested'\n" +
               "- 'analyze case 12'\n" +
               "- 'system statistics'";
    }

    private void saveMessage(String sessionId, String sender, String content, Session session) {
        try {
            session.beginTransaction();
            ChatSession chatSession = session.get(ChatSession.class, Long.parseLong(sessionId));
            if (chatSession == null) {
                User user = SessionManager.getInstance().getCurrentUser();
                chatSession = new ChatSession(user != null ? user : session.get(User.class, 1L));
                session.persist(chatSession);
            }
            chatSession.addMessage(new ChatMessage(sender, content));
            session.merge(chatSession);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (session.getTransaction().isActive()) session.getTransaction().rollback();
            logger.warn("Could not persist message: {}", e.getMessage());
        }
    }

    public void clearSession(String sessionId) {
        dialogueManager.clearCreation(sessionId);
        dialogueManager.clearPendingUpdate(sessionId);
        logger.info("Session state cleared for: {}", sessionId);
    }

    /** Bridge for old UI callers */
    public String processQuery(String query) {
        return processQuery(query, "1");
    }
}
