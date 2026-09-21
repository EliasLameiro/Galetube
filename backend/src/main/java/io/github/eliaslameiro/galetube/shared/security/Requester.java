package io.github.eliaslameiro.galetube.shared.security;

import java.util.UUID;

/**
 * Quien realiza una petición. Se pasa explícitamente a los servicios para que su lógica
 * no dependa del contexto de seguridad ni del hilo en el que se ejecute.
 */
public record Requester(UUID id, Role role) {

    public boolean can(Permission permission) {
        return role != null && role.can(permission);
    }

    public boolean is(UUID userId) {
        return id != null && id.equals(userId);
    }
}
