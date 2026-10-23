package dev.aiplayer.ai;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Blocks;
import java.util.*;

/** Per-dimension provenance for temporary blocks and observed player constructions. */
public final class OwnedBlocks extends PersistentState {
    private final Map<Long,UUID> owners=new HashMap<>();
    private final Set<Long> playerBlocks=new HashSet<>();
    public static OwnedBlocks get(ServerWorld world){
        return world.getPersistentStateManager().getOrCreate(OwnedBlocks::read,OwnedBlocks::new,"aiplayer_block_ownership");
    }
    public void claim(BlockPos pos,UUID owner){owners.put(pos.asLong(),owner);playerBlocks.remove(pos.asLong());markDirty();}
    public boolean claimed(BlockPos pos){return owners.containsKey(pos.asLong());}
    public boolean owned(BlockPos pos,UUID owner){return owner.equals(owners.get(pos.asLong()));}
    public boolean protectedBlock(BlockPos pos){return playerBlocks.contains(pos.asLong());}
    public void release(BlockPos pos){if(owners.remove(pos.asLong())!=null)markDirty();}
    public void playerPlaced(ServerWorld world,BlockPos pos){
        owners.remove(pos.asLong());
        var state=world.getBlockState(pos);
        playerBlocks.add(pos.asLong());
        markDirty();
    }
    public void playerBroke(BlockPos pos){owners.remove(pos.asLong());playerBlocks.remove(pos.asLong());markDirty();}
    public static OwnedBlocks read(NbtCompound root){
        OwnedBlocks state=new OwnedBlocks();
        NbtList list=root.getList("Owners",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){var n=list.getCompound(i);if(n.containsUuid("Owner"))state.owners.put(n.getLong("Pos"),n.getUuid("Owner"));}
        for(long pos:root.getLongArray("Protected"))state.playerBlocks.add(pos);
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound root){
        NbtList list=new NbtList();
        owners.forEach((pos,owner)->{var n=new NbtCompound();n.putLong("Pos",pos);n.putUuid("Owner",owner);list.add(n);});
        root.put("Owners",list);root.putLongArray("Protected",playerBlocks.stream().mapToLong(Long::longValue).toArray());
        return root;
    }
}
