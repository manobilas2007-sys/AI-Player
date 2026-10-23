package dev.aiplayer.compat;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.entity.CompanionEntity;
import fr.rakambda.fallingtree.fabric.FallingTree;
import fr.rakambda.fallingtree.fabric.common.wrapper.*;
import fr.rakambda.fallingtree.common.wrapper.*;
import fr.rakambda.fallingtree.common.config.enums.*;
import fr.rakambda.fallingtree.common.tree.breaking.ToolDamageHandler;
import fr.rakambda.fallingtree.common.tree.breaking.BreakTreeTooBigException;
import fr.rakambda.fallingtree.common.tree.builder.TreeTooBigException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import java.util.*;

/** FallingTree 4.3.4 detection/configuration, with an NPC-owned synchronous executor.
 * Never impersonates an online player or feeds a mob into player-only casts.
 * Called only after the first reachable log finishes normal physical mining.
 */
public final class FallingTreeBridge {
    private FallingTreeBridge() {}
    private record Cut(BlockPos pos, BlockState state, List<ItemStack> drops) {}

    public static boolean tryFell(CompanionEntity npc, BlockPos hit) {
        if(!(npc.getWorld() instanceof ServerWorld world) || !npc.gathering().isNaturalTreeLog(hit)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))return false;
        ItemStack tool=npc.getMainHandStack();
        if(!(tool.getItem() instanceof AxeItem) || ResourcePlan.worn(tool))return false;
        var mod=FallingTree.getMod();
        var config=mod.getConfiguration();
        // Other modes schedule moving blocks using player-specific callbacks.
        // Keep ordinary NPC mining/climbing for them rather than unsafe emulation.
        if(!config.getTrees().isTreeBreaking() || config.getTrees().getBreakMode()!=BreakMode.INSTANTANEOUS
                || !mod.isValidTool(new ItemStackWrapper(tool)))return false;
        try {
            var detected=mod.getTreeBuilder().getTree(new Worker(npc),new LevelWrapper(world),new BlockPosWrapper(hit));
            if(detected.isEmpty())return false;
            var tree=detected.get();
            var parts=tree.getLogs().stream().sorted(config.getTrees().getBreakOrder().getComparator()).toList();
            if(parts.size()<2 || parts.size()>160)return false;
            var damage=new ToolDamageHandler(new ItemStackWrapper(tool),config.getTools().getDamageMultiplicand(),
                true,parts.size(),config.getTrees().getMaxSize(),config.getTrees().getMaxSizeAction(),config.getTools().getDamageRounding());
            // A partial batch would break the tree in an order we cannot resume
            // safely here. Ordinary block mining handles insufficient durability.
            if(damage.getMaxBreakCount()<parts.size())return false;
            int cost=Math.max(1,damage.getActualDamage(parts.size()));
            if(cost>=tool.getMaxDamage()-tool.getDamage())return false;
            List<Cut> cuts=new ArrayList<>();
            SimpleInventory capacity=new SimpleInventory(npc.backpack.size());
            for(int i=0;i<npc.backpack.size();i++)capacity.setStack(i,npc.backpack.getStack(i).copy());
            var ownership=OwnedBlocks.get(world);
            for(var part:parts) {
                var q=part.blockPos();BlockPos pos=new BlockPos(q.getX(),q.getY(),q.getZ());
                if(!world.isChunkLoaded(pos) || !npc.gathering().isNaturalTreeLog(pos)
                        || ownership.protectedBlock(pos) || ownership.claimed(pos)
                        || world.getBlockEntity(pos)!=null || !world.getFluidState(pos).isEmpty())return false;
                BlockState state=world.getBlockState(pos);
                if(!state.isIn(BlockTags.LOGS) || state.getHardness(world,pos)<0)return false;
                // Never remove footing under the worker, an owner or another mob.
                if(!world.getOtherEntities(null,new Box(pos.up()).stretch(0,1,0),e->e.isAlive()).isEmpty())return false;
                List<ItemStack> drops=Block.getDroppedStacks(state,world,pos,null,npc,tool);
                for(ItemStack drop:drops)if(!capacity.addStack(drop.copy()).isEmpty())return false;
                cuts.add(new Cut(pos,state,drops));
            }
            if(cuts.stream().noneMatch(c->c.pos().equals(hit)))return false;
            // Everything is preflighted on the server thread. Each successful
            // removal creates its loot once at that block; inventory pickup stays physical.
            // Remove the physically mined hit first, so a partial execution
            // cannot report that hit completed while it still exists.
            cuts.sort(Comparator.comparingInt(c->c.pos().equals(hit)?0:1));
            int broken=0;
            for(Cut cut:cuts) {
                if(!world.getBlockState(cut.pos()).equals(cut.state()) || !world.breakBlock(cut.pos(),false,npc))break;
                for(ItemStack drop:cut.drops()) {
                    ItemEntity entity=new ItemEntity(world,cut.pos().getX()+0.5,cut.pos().getY()+0.5,cut.pos().getZ()+0.5,drop);
                    entity.setToDefaultPickupDelay();entity.addCommandTag("aiplayer_drop_"+npc.getUuid());world.spawnEntity(entity);
                }
                broken++;
            }
            if(broken==0)return false;
            tool.damage(Math.max(1,damage.getActualDamage(broken)),npc,e->e.sendToolBreakStatus(Hand.MAIN_HAND));
            npc.backpack.markDirty();
            return true;
        } catch(TreeTooBigException | BreakTreeTooBigException ex) {
            return false; // Normal mining and physical climbing remain available.
        }
    }

    /** Read-only actor for the common detector. No player-only breaking APIs use it. */
    private record Worker(CompanionEntity npc) implements IPlayer {
        public Object getRaw(){return npc;}
        public void sendMessage(IComponent message,NotificationMode mode){}
        public IItemStack getMainHandItem(){return new ItemStackWrapper(npc.getMainHandStack());}
        public void awardItemUsed(IItem item){}
        public boolean isCreative(){return false;}
        public boolean isCrouching(){return false;}
        public UUID getUUID(){return npc.getUuid();}
        public ILevel getLevel(){return new LevelWrapper(npc.getWorld());}
        public Set<String> getTags(){return npc.getCommandTags();}
        public boolean addTag(String tag){return npc.addCommandTag(tag);}
        public boolean removeTag(String tag){return npc.removeScoreboardTag(tag);}
    }
}
