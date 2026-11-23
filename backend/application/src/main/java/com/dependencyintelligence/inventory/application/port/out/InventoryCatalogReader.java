package com.dependencyintelligence.inventory.application.port.out;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;
import java.util.List;
import java.util.UUID;

public interface InventoryCatalogReader {
    CatalogPage packages(UUID organizationId, List<UUID> repositories, Filters filters, Page page);
    OccurrencePage occurrences(UUID organizationId, List<UUID> repositories, Filters filters, PackageKey key, Page page);
}
