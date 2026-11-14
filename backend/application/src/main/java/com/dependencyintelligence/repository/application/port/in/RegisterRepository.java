package com.dependencyintelligence.repository.application.port.in;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.dto.RepositoryResult;

public interface RegisterRepository {
    RepositoryResult execute(RegisterRepositoryCommand command);
}
