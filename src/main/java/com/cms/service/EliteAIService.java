package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EliteAIService {
    private static final Logger logger = LoggerFactory.getLogger(EliteAIService.class);

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama3-70b-8192";
    private static final int    MAX_TOKENS = 1200;
    private static final int    TIMEOUT    = 20;

    private final HttpClient   httpClient;
    private final ObjectMapper mapper;
    private final Map<String, java.util.Deque<Map<String,String>>> histories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 14;

    public EliteAIService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
    }

    private String getApiKey() {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            try {
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                key = dotenv.get("GROQ_API_KEY");
            } catch (Exception ignore) {}
        }
        if (key == null || key.isBlank()) key = System.getProperty("GROQ_API_KEY");
        
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY not found in environment.");
        }
        return key.trim();
    }

    public String processMessage(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.isBlank())
            return "Looks like your message was empty — what can I help you with?";
        String dbCtx = buildDatabaseContext();
        try {
            return callGroqAPI(userMessage, sessionId, dbCtx, getApiKey());
        } catch (Exception e) {
            logger.warn("Groq API unavailable ({}), using local engine.", e.getMessage());
            return smartLocalResponse(userMessage, sessionId, dbCtx);
        }
    }

    private String callGroqAPI(String msg, String sessionId, String dbCtx, String apiKey) throws Exception {
        java.util.Deque<Map<String,String>> history =
            histories.computeIfAbsent(sessionId, k -> new java.util.ArrayDeque<>());

        ObjectNode body = mapper.createObjectNode();
        body.put("model", GROQ_MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", 0.75);
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content",
            "You are Alex, an intelligent and friendly AI assistant embedded in the Crime Management System (CMS) " +
            "used by Pakistani law enforcement. You have live access to the database snapshot below.\n\n" +
            "Personality:\n" +
            "- Speak naturally like a knowledgeable colleague, not a robot or helpdesk bot.\n" +
            "- Be warm, professional, and concise. Use sentences for simple answers, bullets for lists.\n" +
            "- If the officer's name is known, occasionally use it to make responses feel personal.\n" +
            "- Be proactive: if you see something relevant in the data (e.g. open warrants), mention it.\n" +
            "- Admit uncertainty honestly rather than making up information.\n\n" +
            "Database Snapshot (live):\n" + dbCtx + "\n\n" +
            "Time: " + LocalDateTime.now() + " | Jurisdiction: Pakistan"
        );

        for (Map<String,String> m : history) {
            ObjectNode mn = messages.addObject();
            mn.put("role",    m.get("role"));
            mn.put("content", m.get("content"));
        }
        ObjectNode um = messages.addObject();
        um.put("role", "user");
        um.put("content", msg);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(GROQ_URL))
            .header("Content-Type",  "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(TIMEOUT))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            String answer = mapper.readTree(resp.body())
                .path("choices").get(0).path("message").path("content").asText();
            history.addLast(Map.of("role","user","content",msg));
            history.addLast(Map.of("role","assistant","content",answer));
            while (history.size() > MAX_HISTORY) history.pollFirst();
            return answer;
        }
        throw new RuntimeException("Groq HTTP " + resp.statusCode());
    }

    private String smartLocalResponse(String message, String sessionId, String dbCtx) {
        String msg = message.toLowerCase().trim();
        if (matchesAny(msg,"hello","hi","hey","salaam","assalam"))  return greeting();
        if (matchesAny(msg,"help","what can you","commands"))        return helpText();
        if (matchesAny(msg,"stats","statistic","overview","total","how many")) return getStatistics();
        if (matchesAny(msg,"active case","open case","pending"))      return getActiveCases();
        if (matchesAny(msg,"recent","latest case","new case"))        return getRecentCases();
        if (matchesAny(msg,"suspect","criminal","person","wanted","fugitive")) return getCriminals();
        if (matchesAny(msg,"officer","detective","staff"))            return getOfficers();
        if (matchesAny(msg,"evidence","exhibit"))                     return getEvidence();
        if (matchesAny(msg,"high risk","dangerous","warrant"))        return getHighRiskPersons();
        if (matchesAny(msg,"crime type","crime categor","offense"))   return getCrimeTypes();
        if (matchesAny(msg,"find","search","look up","who is"))       return handleSearch(msg);
        if (matchesAny(msg,"analyze","analysis","pattern","trend"))   return analyzePatterns();
        if (matchesAny(msg,"today","this week","recent activity"))    return getTodayActivity();
        if (matchesAny(msg,"audit","logs","activity","history"))      return getAuditLogs();
        if (matchesAny(msg,"civilian","regular people"))              return getCivilians();
        if (matchesAny(msg,"thank","thanks","great","awesome"))
            return "Glad I could help! Anything else you need?";
        if (matchesAny(msg,"bye","goodbye","see you"))
            return "Stay safe out there. I'm here whenever you need me.";
        return getIntelligentDefault(msg);
    }

    private String buildDatabaseContext() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            long persons   = q(s,"SELECT COUNT(p) FROM Person p");
            long civilians = q(s,"SELECT COUNT(c) FROM Civilian c");
            long audits    = q(s,"SELECT COUNT(a) FROM AuditLog a");
            long cases     = q(s,"SELECT COUNT(cf) FROM CaseFile cf");
            long open      = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status=:st",Long.class)
                              .setParameter("st", CaseStatus.OPEN).uniqueResult();
            long officers  = q(s,"SELECT COUNT(u) FROM User u");
            long evidence  = q(s,"SELECT COUNT(e) FROM Evidence e");
            long warrants  = s.createQuery("SELECT COUNT(p) FROM Person p WHERE p.deletedAt IS NULL AND EXISTS (SELECT w FROM Warrant w WHERE w.suspect = p AND w.status = :ws)",Long.class)
                              .setParameter("ws", WarrantStatus.ISSUED).uniqueResult();
            return String.format("Persons: %d | Civilians: %d | Audit Logs: %d | Cases: %d | Open: %d | Officers: %d | Evidence: %d | Warrants: %d",
                persons, civilians, audits, cases, open, officers, evidence, warrants);
        } catch (Exception e) { return "Database context temporarily unavailable."; }
    }
    private long q(Session s, String hql) { return s.createQuery(hql,Long.class).uniqueResult(); }

    private String greeting() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            long open = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status=:st",Long.class)
                         .setParameter("st", CaseStatus.OPEN).uniqueResult();
            User u = SessionManager.getInstance().getCurrentUser();
            String first = u != null ? u.getFullName().split(" ")[0] : "Officer";
            return "Hey " + first + "! Good to see you.\n\n" +
                   "You've got " + open + " open case" + (open == 1 ? "" : "s") + " right now. " +
                   "I'm connected to the full database and ready to help — just ask me anything.\n\n" +
                   "Try: \"show me active cases\", \"find Ali Hassan\", \"give me system statistics\", or just chat.";
        } catch (Exception e) {
            return "Hey there! I'm Alex, your CMS assistant. How can I help you today?";
        }
    }

    private String getStatistics() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            long persons  = q(s,"SELECT COUNT(p) FROM Person p");
            long cases    = q(s,"SELECT COUNT(cf) FROM CaseFile cf");
            long open     = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status=:st",Long.class).setParameter("st", CaseStatus.OPEN).uniqueResult();
            long closed   = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status IN (:stList)",Long.class)
                             .setParameter("stList", List.of(CaseStatus.CLOSED_CONVICTED, CaseStatus.CLOSED_ACQUITTED, CaseStatus.CLOSED_UNSOLVED)).uniqueResult();
            long officers = q(s,"SELECT COUNT(u) FROM User u");
            long evidence = q(s,"SELECT COUNT(e) FROM Evidence e");
            long warrants = s.createQuery("SELECT COUNT(p) FROM Person p WHERE p.deletedAt IS NULL AND EXISTS (SELECT w FROM Warrant w WHERE w.suspect = p AND w.status = :ws)",Long.class)
                             .setParameter("ws", WarrantStatus.ISSUED).uniqueResult();
            double rate   = cases > 0 ? (double)(closed)/(cases)*100 : 0;

            return "Here's a quick overview of the system right now:\n\n" +
                   "• Registered persons/suspects: **" + persons + "**\n" +
                   "• Total case files: **" + cases + "**\n" +
                   "• Open cases needing attention: **" + open + "**\n" +
                   "• Closed cases: **" + closed + "** (" + String.format("%.1f%%",rate) + " closure rate)\n" +
                   "• Active officers on system: **" + officers + "**\n" +
                   "• Evidence items logged: **" + evidence + "**\n" +
                   "• People with active warrants: **" + warrants + "**";
        } catch (Exception e) { return "Sorry, I couldn't pull the statistics right now: " + e.getMessage(); }
    }

    private String getActiveCases() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<CaseFile> cases = s.createQuery("FROM CaseFile cf WHERE cf.status=:st ORDER BY cf.id DESC",CaseFile.class)
                .setParameter("st", CaseStatus.OPEN).setMaxResults(8).list();
            if (cases.isEmpty()) return "Good news — there are no open cases at the moment!";
            StringBuilder sb = new StringBuilder("Here are the currently open cases (" + cases.size() + " shown):\n\n");
            for (CaseFile cf : cases) {
                sb.append("• **").append(cf.getCaseNumber()).append("**");
                if (cf.getIncident() != null) sb.append(" — ").append(cf.getIncident().getTitle());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Couldn't fetch open cases: " + e.getMessage(); }
    }

    private String getRecentCases() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<CaseFile> cases = s.createQuery("FROM CaseFile cf ORDER BY cf.id DESC",CaseFile.class)
                .setMaxResults(6).list();
            if (cases.isEmpty()) return "No case files found in the database yet.";
            StringBuilder sb = new StringBuilder("The most recent cases are:\n\n");
            for (CaseFile cf : cases) {
                sb.append("• **").append(cf.getCaseNumber()).append("** [").append(cf.getStatus()).append("]");
                if (cf.getIncident() != null) sb.append(" — ").append(cf.getIncident().getTitle());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching recent cases: " + e.getMessage(); }
    }

    private String getCriminals() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Person> persons = s.createQuery("FROM Person p ORDER BY p.id DESC",Person.class)
                .setMaxResults(8).list();
            if (persons.isEmpty()) return "The person registry is currently empty.";
            long total = q(s,"SELECT COUNT(p) FROM Person p");
            StringBuilder sb = new StringBuilder("Here are the latest registered persons/suspects:\n\n");
            for (Person p : persons) {
                sb.append("• **").append(p.getFirstName()).append(" ").append(p.getLastName()).append("**");
                if (p.getPersonStatus() != null) sb.append(" [").append(p.getPersonStatus()).append("]");
                if (p.isHasActiveWarrant()) sb.append(" ⚠️ Active warrant");
                sb.append("\n");
            }
            sb.append("\n_Showing 8 of ").append(total).append(" total records._");
            return sb.toString();
        } catch (Exception e) { return "Error fetching persons: " + e.getMessage(); }
    }

    private String getOfficers() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<User> officers = s.createQuery("FROM User u ORDER BY u.role",User.class).setMaxResults(12).list();
            if (officers.isEmpty()) return "No officers found in the system.";
            StringBuilder sb = new StringBuilder("Here's the current roster:\n\n");
            for (User u : officers) {
                sb.append("• **").append(u.getFullName()).append("** — ").append(u.getRole());
                if (u.getBadgeNumber() != null) sb.append(" [#").append(u.getBadgeNumber()).append("]");
                if (u.getPrecinct() != null) sb.append(", ").append(u.getPrecinct());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching officers: " + e.getMessage(); }
    }

    private String getEvidence() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Evidence> items = s.createQuery("FROM Evidence e ORDER BY e.id DESC",Evidence.class).setMaxResults(8).list();
            if (items.isEmpty()) return "No evidence has been logged yet.";
            StringBuilder sb = new StringBuilder("Recent evidence items:\n\n");
            for (Evidence e : items) {
                sb.append("• **").append(e.getEvidenceNumber()).append("** [").append(e.getType()).append("]");
                if (e.getStatus() != null) sb.append(" — ").append(e.getStatus());
                if (e.getCaseFile() != null) sb.append(" | Case: ").append(e.getCaseFile().getCaseNumber());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching evidence: " + e.getMessage(); }
    }

    private String getHighRiskPersons() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Person> wanted = s.createQuery("FROM Person p WHERE p.hasActiveWarrant=true ORDER BY p.id DESC",Person.class)
                .setMaxResults(10).list();
            if (wanted.isEmpty()) return "There are currently no persons with active warrants — all clear!";
            StringBuilder sb = new StringBuilder("⚠️ The following " + wanted.size() + " person(s) have active warrants:\n\n");
            for (Person p : wanted) {
                sb.append("• **").append(p.getFirstName()).append(" ").append(p.getLastName()).append("**");
                if (p.getPersonStatus() != null) sb.append(" — ").append(p.getPersonStatus());
                if (p.getDistrict() != null) sb.append(", ").append(p.getDistrict().getName());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching warrant list: " + e.getMessage(); }
    }

    private String getCrimeTypes() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<CrimeType> types = s.createQuery("FROM CrimeType ct ORDER BY ct.name",CrimeType.class).list();
            if (types.isEmpty()) return "No crime categories configured yet.";
            StringBuilder sb = new StringBuilder("Here are all the crime categories in the system:\n\n");
            for (CrimeType ct : types) {
                sb.append("• **").append(ct.getName()).append("** [").append(ct.getCode()).append("]");
                if (ct.getDescription() != null) sb.append(" — ").append(ct.getDescription());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching crime types: " + e.getMessage(); }
    }

    private String handleSearch(String msg) {
        String term = msg.replaceAll("(find|search|look up|show me|who is|where is)\\s*","").trim();
        if (term.isEmpty()) return "What are you looking for? Try something like \"find Ali Hassan\" or \"search robbery cases\".";
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Person> persons = s.createQuery("FROM Person p WHERE LOWER(p.firstName) LIKE :t OR LOWER(p.lastName) LIKE :t OR p.nationalId LIKE :t",Person.class)
                .setParameter("t","%"+term+"%").setMaxResults(6).list();
            List<CaseFile> cases = s.createQuery("FROM CaseFile cf WHERE LOWER(cf.caseNumber) LIKE :t",CaseFile.class)
                .setParameter("t","%"+term+"%").setMaxResults(5).list();

            if (persons.isEmpty() && cases.isEmpty())
                return "I searched the database for \"" + term + "\" but didn't find any matching records. Try a different name or case number.";

            StringBuilder sb = new StringBuilder("Here's what I found for \"" + term + "\":\n\n");
            if (!persons.isEmpty()) {
                sb.append("**Persons:**\n");
                for (Person p : persons)
                    sb.append("  • ").append(p.getFirstName()).append(" ").append(p.getLastName())
                      .append(" [").append(p.getPersonStatus()).append("]")
                      .append(p.isHasActiveWarrant() ? " ⚠️ Warrant" : "").append("\n");
            }
            if (!cases.isEmpty()) {
                sb.append("**Cases:**\n");
                for (CaseFile cf : cases)
                    sb.append("  • ").append(cf.getCaseNumber()).append(" [").append(cf.getStatus()).append("]").append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Search error: " + e.getMessage(); }
    }

    private String analyzePatterns() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> top = s.createQuery(
                "SELECT ct.name, COUNT(i) FROM CrimeIncident i JOIN i.crimeType ct GROUP BY ct.name ORDER BY COUNT(i) DESC",
                Object[].class).setMaxResults(5).list();

            StringBuilder sb = new StringBuilder("Here's what the data tells us about crime patterns:\n\n");
            sb.append("**Top crime types by incident count:**\n");
            int r = 1;
            for (Object[] row : top)
                sb.append(r++).append(". **").append(row[0]).append("** — ").append(row[1]).append(" incident").append((long)row[1]==1?"":"s").append("\n");

            long open   = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status=:st",Long.class).setParameter("st", CaseStatus.OPEN).uniqueResult();
            long closed = s.createQuery("SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status IN (:stList)",Long.class)
                             .setParameter("stList", List.of(CaseStatus.CLOSED_CONVICTED, CaseStatus.CLOSED_ACQUITTED, CaseStatus.CLOSED_UNSOLVED)).uniqueResult();
            long total  = open + closed;
            if (total > 0) {
                double rate = (double)closed/total*100;
                sb.append("\n**Case resolution rate:** ").append(String.format("%.1f%%", rate))
                  .append(" (").append(closed).append(" closed out of ").append(total).append(" total)\n");
                if (rate < 50) sb.append("\n⚠️ Resolution rate is below 50%. Consider reviewing resource allocation.");
            }
            return sb.toString();
        } catch (Exception e) { return "Analysis error: " + e.getMessage(); }
    }

    private String getAuditLogs() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<AuditLog> logs = s.createQuery("FROM AuditLog a ORDER BY a.timestamp DESC", AuditLog.class)
                .setMaxResults(8).list();
            if (logs.isEmpty()) return "No system audit logs found.";
            StringBuilder sb = new StringBuilder("Here are the latest system activities recorded:\n\n");
            for (AuditLog al : logs) {
                sb.append("• **").append(al.getAction()).append("** by ").append(al.getUserName())
                  .append(" — ").append(al.getDescription()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching audit logs: " + e.getMessage(); }
    }

    private String getCivilians() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Civilian> civs = s.createQuery("FROM Civilian c JOIN FETCH c.person p ORDER BY c.id DESC", Civilian.class)
                .setMaxResults(8).list();
            if (civs.isEmpty()) return "No civilians registered in the database.";
            StringBuilder sb = new StringBuilder("Here are the latest registered civilians:\n\n");
            for (Civilian c : civs) {
                sb.append("• **").append(c.getPerson().getFirstName()).append(" ").append(c.getPerson().getLastName()).append("** — ")
                  .append(c.getOccupation() != null ? c.getOccupation() : "General")
                  .append(" at ").append(c.getEmployer() != null ? c.getEmployer() : "Private").append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Error fetching civilians: " + e.getMessage(); }
    }

    private String getTodayActivity() { return getRecentCases() + "\n\n---\n\n" + getStatistics(); }

    private String getIntelligentDefault(String msg) {
        return "I'm not quite sure what you mean by \"" + msg + "\", but I'm happy to help.\n\n" +
               "Here are some things you can ask me:\n" +
               "• **\"statistics\"** — Full system overview\n" +
               "• **\"active cases\"** — Open investigations\n" +
               "• **\"find [name]\"** — Search persons or cases\n" +
               "• **\"criminals\"** — Suspect registry\n" +
               "• **\"evidence\"** — Evidence log\n" +
               "• **\"analyze\"** — Crime patterns and trends\n" +
               "• **\"high risk\"** — Persons with active warrants\n\n" +
               "Or just ask me a question naturally — I'll do my best to understand!";
    }

    private String helpText() {
        return "Sure, here's what I can do for you:\n\n" +
               "**Query data:**\n" +
               "• \"give me system statistics\" — numbers on everything\n" +
               "• \"show active / recent cases\" — case overviews\n" +
               "• \"list all criminals\" — suspect registry\n" +
               "• \"who are the officers?\" — staff list\n" +
               "• \"show evidence\" — evidence log summary\n" +
               "• \"who has active warrants?\" — wanted persons\n\n" +
               "**Search:**\n" +
               "• \"find Ali Hassan\" — search by name\n" +
               "• \"search CASE-2026-001\" — look up a case\n\n" +
               "**Analysis:**\n" +
               "• \"analyze crime patterns\" — trends and hotspots\n" +
               "• \"what happened today?\" — recent activity\n\n" +
               "You can also just ask me questions naturally — I'm connected to the live database!";
    }

    private boolean matchesAny(String msg, String... keywords) {
        for (String kw : keywords) if (msg.contains(kw)) return true;
        return false;
    }

    public void clearSession(String sessionId) { histories.remove(sessionId); }
}
