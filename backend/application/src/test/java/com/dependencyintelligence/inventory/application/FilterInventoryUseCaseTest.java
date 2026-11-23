package com.dependencyintelligence.inventory.application;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace.Repository;
import com.dependencyintelligence.inventory.application.port.out.InventoryCatalogReader;
import com.dependencyintelligence.inventory.application.usecase.FilterInventoryUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilterInventoryUseCaseTest {
    @Test void restrictsCatalogAndDetailsToVisibleRepositoriesInTheRequestedOrganization() {
        UUID user = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        UUID otherOrganization = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        var service = new FilterInventoryUseCase(ignored -> List.of(
            new Repository(visible, organization, "Current", "visible", "main", "github"),
            new Repository(outside, otherOrganization, "Other", "outside", "main", "local")),
            new InventoryCatalogReader() {
                @Override public CatalogPage packages(UUID id, List<UUID> repositories, Filters filters, Page page) {
                    assertEquals(organization, id);
                    assertEquals(List.of(visible), repositories);
                    return new CatalogPage(List.of(), 1, 1);
                }
                @Override public OccurrencePage occurrences(UUID id, List<UUID> repositories, Filters filters,
                                                             PackageKey key, Page page) {
                    assertEquals(organization, id);
                    assertEquals(List.of(visible), repositories);
                    return new OccurrencePage(List.of(), 1);
                }
            });
        var page = new Page(0, 25);
        var key = new PackageKey("npm", "@scope", "@scope/widget");
        assertEquals(1, service.packages(user, organization, filters(null), page).totalPackages());
        assertEquals(0, service.packages(user, organization, filters(outside), page).totalPackages());
        assertEquals(0, service.occurrences(user, organization, filters(hidden), key, page).totalOccurrences());
        assertEquals(1, service.occurrences(user, organization, filters(visible), key, page).totalOccurrences());
    }

    private static Filters filters(UUID repository) {
        return new Filters(repository, null, null, null, null, null, null, null, false);
    }
}
