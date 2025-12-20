package net.aaavein.supply;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(Supply.MODID)
public class Supply {
    public static final String MODID = "supply";

    public Supply(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Demand.SPEC, "supply.toml");
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}