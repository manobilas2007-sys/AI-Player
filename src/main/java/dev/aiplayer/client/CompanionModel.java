package dev.aiplayer.client;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Use the same dominant arm as vanilla HeldItemFeatureRenderer, including left-handed mobs. */
public final class CompanionModel extends PlayerEntityModel<CompanionEntity> {
    public CompanionModel(ModelPart root) { super(root,false); }
    @Override public void setAngles(CompanionEntity entity, float limbAngle, float limbDistance,
                                    float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity,limbAngle,limbDistance,animationProgress,headYaw,headPitch);
        boolean left=entity.getMainArm()==net.minecraft.util.Arm.LEFT;
        ModelPart arm=left?leftArm:rightArm;
        float mirror=left?-1:1;
        if(entity.attackVisualTicks()>0 && !entity.isEating()) {
            float phase=(8-entity.attackVisualTicks()+animationProgress-entity.age)/8.0f;
            phase=Math.max(0,Math.min(1,phase));
            arm.pitch=-0.35f-(float)Math.sin(phase*Math.PI)*1.9f;
            arm.yaw=-0.6f+(float)Math.sin(phase*Math.PI)*0.9f;
            arm.roll=0.12f;
        } else if(entity.isWorkingVisual() && !entity.isEating()) {
            arm.pitch=-1.05f+(float)Math.sin(animationProgress*1.05f)*0.85f;
            arm.yaw=-0.12f;
            arm.roll=0.05f;
        }
        if(!entity.isEating() && !entity.isWorkingVisual() && entity.attackVisualTicks()==0) {
            switch(entity.interactionVisual()) {
                case CompanionEntity.PLACE -> {arm.pitch=-1.25f;arm.yaw=-0.15f;arm.roll=0;}
                case CompanionEntity.CRAFT -> {arm.pitch=-0.8f+(float)Math.sin(animationProgress*0.65f)*0.12f;arm.yaw=-0.3f;arm.roll=0.08f;}
                case CompanionEntity.EQUIP -> {arm.pitch=0.3f;arm.yaw=-0.2f;arm.roll=0.15f;}
                default -> {}
            }
        }
        if (entity.isEating()) {
            arm.pitch = -1.30f + (float)Math.sin(animationProgress*1.5f)*0.12f;
            arm.yaw = -0.45f;
            arm.roll = -0.08f;
        }
        if(entity.attackVisualTicks()>0 || entity.isWorkingVisual() || entity.isEating() || entity.interactionVisual()!=0) {
            arm.yaw*=mirror;arm.roll*=mirror;
        }
        rightSleeve.copyTransform(rightArm);
        leftSleeve.copyTransform(leftArm);
    }
}
