package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.domain.Scan;

public interface ScanStore {
    Scan add(Scan scan);
}
