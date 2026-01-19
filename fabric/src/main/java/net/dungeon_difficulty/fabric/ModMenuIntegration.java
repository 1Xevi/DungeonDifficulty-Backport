package net.dungeon_difficulty.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.dungeon_difficulty.fabric.client.*;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            GuiUtils.clearCache();
            return GuiBuilder.create(parent);
        };
    }
}