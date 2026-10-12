package com.jiafenle.spawncapchanger;

import com.jiafenle.spawncapchanger.command.SpawnCapCommand;
import com.jiafenle.spawncapchanger.core.SpawnCapManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Main entry point of Spawn Cap Changer.
 * <p>
 * The same jar runs on the physical client (singleplayer / LAN) and the dedicated server.
 * All gameplay behaviour lives on the (integrated or dedicated) logical server.
 */
@Mod(SpawnCapChangerMod.MOD_ID)
public class SpawnCapChangerMod {

    public static final String MOD_ID = "spawncapchanger";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpawnCapChangerMod(IEventBus modEventBus) {
        IEventBus gameEventBus = NeoForge.EVENT_BUS;
        gameEventBus.addListener(this::onServerStarting);
        gameEventBus.addListener(this::onRegisterCommands);
        LOGGER.info("[Spawn Cap Changer] Mod loaded. Use /spawncap to change natural mob spawn caps.");
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Loads the persisted caps for this save and applies them to MobCategory right away.
        SpawnCapManager.attach(event.getServer());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SpawnCapCommand.register(event.getDispatcher());
    }
}
