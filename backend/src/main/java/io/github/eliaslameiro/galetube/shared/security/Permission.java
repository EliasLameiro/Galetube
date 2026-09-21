package io.github.eliaslameiro.galetube.shared.security;

/**
 * Capacidades concretas que un rol puede conceder. Los servicios preguntan por estas,
 * nunca por el rol, para que añadir un rol nuevo no obligue a tocar su lógica.
 */
public enum Permission {
    VIEW_ANY_VIDEO,
    EDIT_ANY_VIDEO,
    DELETE_ANY_VIDEO
}
