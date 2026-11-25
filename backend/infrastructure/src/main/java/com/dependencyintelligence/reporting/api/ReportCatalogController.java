package com.dependencyintelligence.reporting.api;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.inventory.application.port.out.InventoryCatalogReader;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports read only the same authorized, tenant-scoped catalog as Inventory. */
@RestController
@RequestMapping("/api/organizations/{organizationId}/reports")
public final class ReportCatalogController {
    private final InventoryCatalogReader inventory;
    private final AuthorizedRepositoryActions access;
    public ReportCatalogController(InventoryCatalogReader inventory, AuthorizedRepositoryActions access) {
        this.inventory = inventory; this.access = access;
    }

    @GetMapping("/preview")
    public CatalogPage preview(@PathVariable UUID organizationId, @RequestParam UUID repositoryId,
            @RequestParam(defaultValue = "ALL") String ecosystem, @RequestParam(defaultValue = "ALL") String severity,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, Principal principal) {
        return packages(principal, organizationId, repositoryId, ecosystem, severity, page, size, "report.read");
    }

    @GetMapping("/export-data")
    public CatalogPage exportData(@PathVariable UUID organizationId, @RequestParam UUID repositoryId,
            @RequestParam(defaultValue = "ALL") String ecosystem, @RequestParam(defaultValue = "ALL") String severity,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, Principal principal) {
        return packages(principal, organizationId, repositoryId, ecosystem, severity, page, size, "report.export");
    }

    private CatalogPage packages(Principal principal, UUID organizationId, UUID repositoryId, String ecosystem,
            String severity, int page, int size, String permission) {
        Page boundedPage = new Page(page, size);
        Filters filters = new Filters(repositoryId, "", ecosystem, "", "", "", "", severity, false);
        // Both checks and the catalog read stay on the selected tenant connection.
        return access.execute(principal, organizationId, repositoryId, permission,
            () -> access.execute(principal, organizationId, repositoryId, "repository.read",
                () -> inventory.packages(organizationId, List.of(repositoryId), filters, boundedPage)));
    }
}
