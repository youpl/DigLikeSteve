package io.github.youpl.diglikesteve.core.shared;

import java.util.Objects;
import java.util.UUID;

/** Identifiant d'une partie. Sert aussi de clé d'idempotence pour les récompenses. */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId parse(String raw) {
        return new SessionId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
