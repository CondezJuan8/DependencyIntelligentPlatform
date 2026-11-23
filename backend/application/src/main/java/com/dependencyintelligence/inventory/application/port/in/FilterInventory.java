package com.dependencyintelligence.inventory.application.port.in;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;
import java.util.UUID;

public interface FilterInventory {
    CatalogPage packages(UUID userId, UUID organizationId, Filters filters, Page page);
    OccurrencePage occurrences(UUID userId, UUID organizationId, Filters filters, PackageKey key, Page page);
}
