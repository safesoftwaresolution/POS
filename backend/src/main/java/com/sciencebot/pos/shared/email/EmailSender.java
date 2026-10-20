package com.sciencebot.pos.shared.email;

public interface EmailSender {
    void send(String to, String subject, String htmlBody);
}
