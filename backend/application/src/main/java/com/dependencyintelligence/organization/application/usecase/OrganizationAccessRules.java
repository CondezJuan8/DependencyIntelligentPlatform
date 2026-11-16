package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.dto.AccessModels.Actor;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.util.Set;
import java.util.UUID;

public final class OrganizationAccessRules {
    public static final Set<String> REPOSITORY_PERMISSIONS = Set.of("repository.read", "scan.read", "scan.create",
        "document.read", "document.manage", "assistant.query", "impact.create", "change.simulate", "change.propose", "report.read", "report.export");
    private OrganizationAccessRules() { }

    public static Actor manager(OrganizationAccessStore store, UUID organization, UUID user) {
        store.lockOrganization(organization);
        var actor = store.actor(organization, user);
        if (!actor.manager()) throw new PermissionDeniedException();
        return actor;
    }
    public static void assignable(Actor actor, OrganizationRole role) {
        if (!actor.manager() || (!actor.owner() && (role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN))) {
            throw new PermissionDeniedException();
        }
    }
}
