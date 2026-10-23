package dev.aiplayer.client;
import dev.aiplayer.AIPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
public class AIPlayerClient implements ClientModInitializer {
    public void onInitializeClient(){
        EntityRendererRegistry.register(AIPlayer.COMPANION,CompanionRenderer::new);
        HandledScreens.register(AIPlayer.MENU,CompanionScreen::new);
    }
}
