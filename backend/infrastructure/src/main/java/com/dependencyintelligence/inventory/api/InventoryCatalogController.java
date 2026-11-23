package com.dependencyintelligence.inventory.api;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;
import com.dependencyintelligence.inventory.application.port.in.FilterInventory;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory")
public final class InventoryCatalogController {
    private final FilterInventory catalog;

    public InventoryCatalogController(FilterInventory catalog) { this.catalog = catalog; }

    @GetMapping
    public CatalogPage packages(@PathVariable UUID organizationId, @RequestParam Map<String, String> parameters,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size, Principal principal) {
        return catalog.packages(UUID.fromString(principal.getName()), organizationId, filters(parameters), new Page(page, size));
    }

    @GetMapping("/occurrences")
    public OccurrencePage occurrences(@PathVariable UUID organizationId, @RequestParam Map<String, String> parameters,
            @RequestParam String packageEcosystem, @RequestParam(defaultValue = "") String namespace,
            @RequestParam String name, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size, Principal principal) {
        return catalog.occurrences(UUID.fromString(principal.getName()), organizationId, filters(parameters),
            new PackageKey(packageEcosystem, namespace, name), new Page(page, size));
    }

    private static Filters filters(Map<String, String> parameters) {
        String repository = parameters.getOrDefault("repositoryId", "");
        return new Filters(repository.isBlank() ? null : UUID.fromString(repository), parameters.get("query"),
            parameters.get("ecosystem"), parameters.get("version"), parameters.get("kind"), parameters.get("scope"),
            parameters.get("security"), parameters.get("severity"), Boolean.parseBoolean(parameters.get("riskFirst")));
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ProblemDetail queryTimeout() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "La consulta de Inventario tardó más de lo previsto. Intenta con un repositorio o un filtro.");
    }
}
