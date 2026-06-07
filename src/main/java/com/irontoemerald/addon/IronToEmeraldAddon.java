package com.irontoemerald.addon;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.irontoemerald.addon.modules.IronToEmeraldModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IronToEmeraldAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger(IronToEmeraldAddon.class);

    @Override
    public void onInitialize() {
        LOG.info("Initializing Iron to Emerald Addon");
        Modules.get().add(new IronToEmeraldModule());
    }

    @Override
    public String getPackage() {
        return "com.irontoemerald.addon";
    }

    @Override
    public String getName() {
        return "Iron to Emerald Addon";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
