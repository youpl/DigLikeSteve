package io.github.youpl.diglikesteve.configuration.model;

import java.util.Objects;

/**
 * Stockage durable (DAT §11).
 *
 * @param backend               {@code sqlite} par défaut
 * @param file                  chemin relatif au dossier du plugin
 * @param snapshotRetentionDays rétention des snapshots restaurés avant purge
 * @param busyTimeoutMillis     {@code PRAGMA busy_timeout}
 */
public record StorageSettings(String backend, String file, int snapshotRetentionDays, int busyTimeoutMillis) {

    public StorageSettings {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(file, "file");
        if (snapshotRetentionDays < 0) {
            throw new IllegalArgumentException("snapshotRetentionDays ne peut pas être négatif");
        }
        if (busyTimeoutMillis <= 0) {
            throw new IllegalArgumentException("busyTimeoutMillis doit être positif");
        }
    }

    public static StorageSettings defaults() {
        return new StorageSettings("sqlite", "data/dls.db", 14, 5_000);
    }
}
