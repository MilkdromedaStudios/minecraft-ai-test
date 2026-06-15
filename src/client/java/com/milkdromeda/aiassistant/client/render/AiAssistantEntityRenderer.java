package com.milkdromeda.aiassistant.client.render;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public class AiAssistantEntityRenderer extends
        HumanoidMobRenderer<AiAssistantEntity, AiAssistantEntityRenderState, AiAssistantEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    public AiAssistantEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AiAssistantEntityModel(ctx.bakeLayer(AiAssistantEntityModel.LAYER)), 0.5f);
    }

    @Override
    public AiAssistantEntityRenderState createRenderState() {
        return new AiAssistantEntityRenderState();
    }

    @Override
    public void extractRenderState(AiAssistantEntity entity, AiAssistantEntityRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.assistantName = entity.getAssistantName();
    }

    @Override
    public Identifier getTextureLocation(AiAssistantEntityRenderState state) {
        return TEXTURE;
    }
}
