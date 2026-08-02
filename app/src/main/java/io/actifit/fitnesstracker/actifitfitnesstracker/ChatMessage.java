package io.actifit.fitnesstracker.actifitfitnesstracker;

public class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_AI = "model";

    private final String role;
    private final String text;

    public ChatMessage(String role, String text) {
        this.role = role;
        this.text = text;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }
}