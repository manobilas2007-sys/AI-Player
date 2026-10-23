package dev.aiplayer.client;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.util.Identifier;
public class CompanionRenderer extends MobEntityRenderer<CompanionEntity,CompanionModel> {
    private static final Identifier STEVE=new Identifier("minecraft","textures/entity/player/wide/steve.png");
    public CompanionRenderer(EntityRendererFactory.Context ctx){
        super(ctx,new CompanionModel(ctx.getPart(EntityModelLayers.PLAYER)),0.5f);
        addFeature(new HeldItemFeatureRenderer<>(this,ctx.getHeldItemRenderer()));
        addFeature(new ArmorFeatureRenderer<>(this,new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),ctx.getModelManager()));
    }
    @Override public Identifier getTexture(CompanionEntity entity){return STEVE;}
}
