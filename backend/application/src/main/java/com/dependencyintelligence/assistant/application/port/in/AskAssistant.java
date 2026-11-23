package com.dependencyintelligence.assistant.application.port.in;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.Answer;
import com.dependencyintelligence.assistant.application.dto.AssistantModels.Ask;

public interface AskAssistant { Answer execute(Ask request); }
