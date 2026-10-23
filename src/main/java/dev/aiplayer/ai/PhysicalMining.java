package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameRules;

/** Tick-based survival breaking. Loot is spawned at the block, never inserted remotely. */
public final class PhysicalMining {
    private final CompanionEntity npc;
    private BlockPos target;
    private BlockState original;
    private float progress;
    private int lastTick = -2;
    private final java.util.function.Predicate<ItemStack> allowedTool;
    public PhysicalMining(CompanionEntity npc){this(npc,stack->true);}
    public PhysicalMining(CompanionEntity npc,java.util.function.Predicate<ItemStack> allowedTool){this.npc=npc;this.allowedTool=allowedTool;}
    public boolean reachable(BlockPos pos) {
        Vec3d end = Vec3d.ofCenter(pos);
        if (npc.getEyePos().squaredDistanceTo(end) > 4.5 * 4.5) return false;
        var hit = npc.getWorld().raycast(new RaycastContext(npc.getEyePos(), end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    public void reset() {
        if (target != null) npc.getWorld().setBlockBreakingInfo(npc.getId(), target, -1);
        target = null; original = null; progress = 0;npc.clearWorkingVisual();
    }
    public boolean hasProgress(){return target!=null && progress>0;}
    public BlockPos target(){return target;}
    public boolean tick(BlockPos pos) { return tick(pos, false); }
    public boolean tick(BlockPos pos, boolean reused) {
        if (!(npc.getWorld() instanceof ServerWorld world) || !world.isChunkLoaded(pos)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) || !reachable(pos)) {
            reset(); return false;
        }
        if(OwnedBlocks.get(world).protectedBlock(pos) || (OwnedBlocks.get(world).claimed(pos) && !OwnedBlocks.get(world).owned(pos,npc.getUuid()))){reset();return false;}
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (state.isAir() || hardness < 0 || world.getBlockEntity(pos) != null) { reset(); return false; }
        if (!pos.equals(target) || !state.equals(original) || npc.age - lastTick > 1) {
            reset(); target = pos.toImmutable(); original = state;
        }
        lastTick = npc.age;
        npc.markWorking();
        npc.getNavigation().stop();
        Vec3d center = Vec3d.ofCenter(pos);
        npc.getLookControl().lookAt(center.x, center.y, center.z, 30, 30);
        if (npc.age % 5 == 0) npc.swingHand(Hand.MAIN_HAND);
        npc.equipMiningTool(state,allowedTool);
        ItemStack tool = npc.getMainHandStack();
        if(!allowedTool.test(tool)){reset();return false;}
        if(state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && !tool.isEmpty()
                && (!(tool.getItem() instanceof net.minecraft.item.AxeItem)
                    || dev.aiplayer.ai.brain.ResourcePlan.worn(tool))){reset();return false;}
        boolean harvest = !state.isToolRequired() || tool.isSuitableFor(state);
        float speed = tool.getMiningSpeedMultiplier(state);
        int efficiency=net.minecraft.enchantment.EnchantmentHelper.getLevel(net.minecraft.enchantment.Enchantments.EFFICIENCY,tool);
        if(speed>1 && efficiency>0)speed+=efficiency*efficiency+1;
        var haste=npc.getStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE);
        if(haste!=null)speed*=1+0.2f*(haste.getAmplifier()+1);
        var fatigue=npc.getStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
        if(fatigue!=null)speed*=switch(fatigue.getAmplifier()){case 0->0.3f;case 1->0.09f;case 2->0.0027f;default->0.00081f;};
        if (!npc.isOnGround()) speed /= 5;
        if (npc.isSubmergedInWater()) speed /= 5;
        progress += hardness == 0 ? 1 : speed / hardness / (harvest ? 30 : 100);
        world.setBlockBreakingInfo(npc.getId(), pos, Math.min(9, (int)(progress * 10)));
        if (progress < 1) return false;
        if(!reused && dev.aiplayer.compat.FallingTreeBridge.tryFell(npc,pos)) {
            reset();return true;
        }
        var drops = harvest ? Block.getDroppedStacks(state, world, pos, null, npc, tool)
            : java.util.List.<ItemStack>of();
        net.minecraft.inventory.SimpleInventory capacity=new net.minecraft.inventory.SimpleInventory(npc.backpack.size());
        for(int i=0;i<npc.backpack.size();i++)capacity.setStack(i,npc.backpack.getStack(i).copy());
        for(ItemStack drop:drops)if(!capacity.addStack(drop.copy()).isEmpty()){reset();return false;}
        if (!world.breakBlock(pos, false, npc)) { reset(); return false; }
        OwnedBlocks.get(world).release(pos);
        if (world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))
            for (ItemStack drop : drops) {
                var entity=new net.minecraft.entity.ItemEntity(world,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,drop);
                entity.setToDefaultPickupDelay();entity.addCommandTag("aiplayer_drop_"+npc.getUuid());
                if(reused)entity.addCommandTag("aiplayer_recovered_support");
                world.spawnEntity(entity);
            }
        tool.getItem().postMine(tool, world, state, pos, npc);
        reset(); return true;
    }
}
