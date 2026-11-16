package com.dependencyintelligence.identity.application.port.out;
import com.dependencyintelligence.identity.application.port.in.ReadDevelopmentMail.Message;
import java.util.List;
public interface DevelopmentMailbox {List<Message> find(String email,String requiredMarker);}
