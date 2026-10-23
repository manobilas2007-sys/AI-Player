package dev.aiplayer.mixin;

import dev.aiplayer.ai.OwnedBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("RETURN"))
    private void aiplayer$recordPlacement(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> cir){
        if(cir.getReturnValue().isAccepted() && context.getPlayer()!=null && context.getWorld() instanceof ServerWorld world)
            OwnedBlocks.get(world).playerPlaced(world,context.getBlockPos());
    }
}
