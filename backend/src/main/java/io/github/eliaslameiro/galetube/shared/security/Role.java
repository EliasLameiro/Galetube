package io.github.eliaslameiro.galetube.shared.security;

import java.util.EnumSet;
import java.util.Set;

public enum Role {

    USER(EnumSet.noneOf(Permission.class)),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
