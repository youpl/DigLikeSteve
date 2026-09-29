package io.github.youpl.onedonjon.bridge;

import io.github.youpl.onedonjon.OneDonjonPlugin;

public final class CoreBridges {

    private CoreBridges() {}

    /** OneCore s'il est present et exploitable, sinon la version autonome. */
    public static StandaloneCore create(OneDonjonPlugin plugin) {
        OneCoreAdapter a = OneCoreAdapter.tryCreate(plugin);
        return a != null ? a : new StandaloneCore(plugin);
    }
}
