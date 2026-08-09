package io.github.youpl.diglikesteve.platform.paper.audience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-régression : MiniMessage n'accepte qu'un nom de balise en {@code [a-z0-9_-]}. Un nom en
 * camelCase faisait auparavant échouer le rendu de {@code /dls admin debug} à l'exécution.
 */
class MessageCatalogTest {

    @Test
    @DisplayName("le camelCase est converti en snake_case")
    void camelCaseBecomesSnakeCase() {
        assertEquals("tnt_accounts", MessageCatalog.normalizeTagName("tntAccounts"));
        assertEquals("tracked_tnt", MessageCatalog.normalizeTagName("trackedTnt"));
        assertEquals("blocks_scored", MessageCatalog.normalizeTagName("blocksScored"));
    }

    @Test
    @DisplayName("les noms déjà valides sont laissés intacts")
    void validNamesAreUnchanged() {
        assertEquals("score", MessageCatalog.normalizeTagName("score"));
        assertEquals("time-left", MessageCatalog.normalizeTagName("time-left"));
        assertEquals("best_score", MessageCatalog.normalizeTagName("best_score"));
        assertEquals("rank2", MessageCatalog.normalizeTagName("rank2"));
    }

    @Test
    @DisplayName("tout nom normalisé respecte le motif accepté par MiniMessage")
    void normalizedNamesMatchPattern() {
        String[] inputs = {"tntAccounts", "joueur éligible", "A", "", "a.b/c", "UPPER"};
        for (String input : inputs) {
            String normalized = MessageCatalog.normalizeTagName(input);
            assertTrue(normalized.matches("[a-z0-9_-]+"),
                    "nom invalide produit pour « " + input + " » : " + normalized);
        }
    }
}
