package io.github.youpl.onedonjon.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AuditTest {

    @Test
    void auditLinesCannotForgeEntries() {
        String c = StandaloneCore.clean("steve\n2026-01-01T00:00:00Z admin dungeon.delete x");
        assertFalse(c.contains("\n"));
        assertEquals("-", StandaloneCore.clean(null));
        assertEquals(300, StandaloneCore.clean("x".repeat(1000)).length());
    }
}
