package io.github.youpl.diglikesteve.core.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identité stable d'un joueur. Le domaine n'utilise jamais un pseudo comme clé (DAT §20).
 */
public record PlayerId(UUID value) implements Comparable<PlayerId> {

    public PlayerId {
        Objects.requireNonNull(value, "value");
    }

    public static PlayerId of(UUID uuid) {
        return new PlayerId(uuid);
    }

    public static PlayerId parse(String raw) {
        return new PlayerId(UUID.fromString(raw));
    }

    @Override
    public int compareTo(PlayerId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
