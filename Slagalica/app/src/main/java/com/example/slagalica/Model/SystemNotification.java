package com.example.slagalica.Model;

public class SystemNotification {
    private String id;
    private String title;
    private String message;
    private String type; // CHAT, RANKING, REWARD, OTHER
    private boolean read;
    private long createdAt;
    private String actionType;

    public SystemNotification() {
    }

    public SystemNotification(String id, String title, String message, String type,
                              boolean read, long createdAt, String actionType) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = read;
        this.createdAt = createdAt;
        this.actionType = actionType;
    }

    public String getDisplayText() {
        return (read ? "✓ " : "● ") + "[" + type + "] " + title + "\n" + message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
}