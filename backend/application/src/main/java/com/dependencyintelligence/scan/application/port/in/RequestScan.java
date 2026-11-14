package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.dto.ScanResult;

public interface RequestScan {
    ScanResult execute(RequestScanCommand command);
}
