package com.cms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime startTime;
    private LocalDateTime lastActivity;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession() {
        this.startTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    public ChatSession(User user) {
        this();
        this.user = user;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setSession(this);
        this.lastActivity = LocalDateTime.now();
    }
}
