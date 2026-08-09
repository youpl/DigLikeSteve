package io.github.youpl.diglikesteve.platform.paper;

/**
 * Détection de la plateforme (DAT §3).
 *
 * <p>Déclarer {@code folia-supported: true} est nécessaire mais insuffisant : c'est
 * l'implémentation qui doit respecter l'affinité de région. Cette détection sert seulement à
 * choisir la bonne API de tâche périodique et à refuser certaines intégrations tierces.</p>
 */
public final class FoliaSupport {

    private static final boolean FOLIA = detect();

    private FoliaSupport() {
    }

    private static boolean detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static String platformName() {
        return FOLIA ? "Folia" : "Paper";
    }
}
