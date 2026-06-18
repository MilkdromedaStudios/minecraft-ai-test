package com.milkdromeda.aiassistant.client;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.client.gui.AiConfigScreen;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityModel;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityRenderer;
import com.milkdromeda.aiassistant.network.ConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

public class AiAssistantClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                AiAssistantEntityModel.LAYER,
                AiAssistantEntityModel::createModelData
        );

        EntityRendererRegistry.register(
                ModEntities.AI_ASSISTANT,
                AiAssistantEntityRenderer::new
        );

        // Server sent us the current config (via /ai menu) — open the settings screen.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    FpsGuardian.setPreset(payload.data().performancePreset());
                    context.client().setScreen(new AiConfigScreen(payload.data()));
                }));

        // Extreme frame-rate watchdog: auto-disable the mod if FPS collapses.
        FpsGuardian.register();
    }
}
