package com.dependencyintelligence.inventory.application.usecase;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;
import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import com.dependencyintelligence.inventory.application.port.in.FilterInventory;
import com.dependencyintelligence.inventory.application.port.out.InventoryCatalogReader;
import java.util.List;
import java.util.UUID;

public final class FilterInventoryUseCase implements FilterInventory {
    private final FindAccountWorkspace workspace;
    private final InventoryCatalogReader catalog;

    public FilterInventoryUseCase(FindAccountWorkspace workspace, InventoryCatalogReader catalog) {
        this.workspace = workspace;
        this.catalog = catalog;
    }

    @Override public CatalogPage packages(UUID userId, UUID organizationId, Filters filters, Page page) {
        var ids = visibleRepositories(userId, organizationId, filters.repositoryId());
        return ids.isEmpty() ? new CatalogPage(List.of(), 0, 0) : catalog.packages(organizationId, ids, filters, page);
    }

    @Override public OccurrencePage occurrences(UUID userId, UUID organizationId, Filters filters, PackageKey key, Page page) {
        var ids = visibleRepositories(userId, organizationId, filters.repositoryId());
        return ids.isEmpty() ? new OccurrencePage(List.of(), 0) : catalog.occurrences(organizationId, ids, filters, key, page);
    }

    private List<UUID> visibleRepositories(UUID userId, UUID organizationId, UUID repositoryId) {
        return workspace.repositories(userId).stream()
            .filter(repo -> repo.organizationId().equals(organizationId))
            .map(FindAccountWorkspace.Repository::id)
            .filter(id -> repositoryId == null || repositoryId.equals(id))
            .toList();
    }
}
