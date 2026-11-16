package com.dependencyintelligence.identity.application.port.in;
import java.util.List;
import java.util.UUID;
public interface ReadDevelopmentMail {
    record Message(String subject,String text){@Override public String toString(){return "Message[REDACTED]";}}
    List<Message> account(UUID actor,UUID recipient);
    List<Message> invitation(UUID actor,UUID organization,UUID invitation);
}
