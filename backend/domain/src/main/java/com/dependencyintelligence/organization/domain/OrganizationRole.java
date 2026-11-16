package com.dependencyintelligence.organization.domain;

public enum OrganizationRole {
    OWNER, ADMIN, MEMBER, VIEWER;

    public boolean managesAccess() { return this == OWNER || this == ADMIN; }
    public static OrganizationRole parse(String value) {
        for (var role : values()) if (role.name().equals(value)) return role;
        throw new IllegalArgumentException("Rol de organización no válido");
    }
}
