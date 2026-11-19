package com.dependencyintelligence.repository.application.port.in;

import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand;
import com.dependencyintelligence.repository.application.dto.LocalFolderImportResult;

public interface ImportLocalFolder {
    LocalFolderImportResult execute(LocalFolderImportCommand command);
}
