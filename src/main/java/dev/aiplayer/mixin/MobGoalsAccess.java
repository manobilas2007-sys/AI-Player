package dev.aiplayer.mixin;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MobEntity.class)
public interface MobGoalsAccess {
    @Accessor("targetSelector") GoalSelector aiplayer$getTargetSelector();
}
