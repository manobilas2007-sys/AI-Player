package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.LeavesBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Natural-tree evidence belongs to the worker, not to a particular order.
 * Remember the canopy before the first bootstrap log removes its rooted base. */
public final class TreeHarvestSession {
    private final CompanionEntity npc;
    private final LinkedHashSet<BlockPos> logs=new LinkedHashSet<>();
    private String dimension="";
    public TreeHarvestSession(CompanionEntity npc){this.npc=npc;}
    public boolean contains(BlockPos p){
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.isChunkLoaded(p))return false;
        if(!dimension.equals(world.getRegistryKey().getValue().toString()))logs.clear();
        var ledger=OwnedBlocks.get(world);
        return logs.contains(p) && world.getBlockState(p).isIn(BlockTags.LOGS)
            && !ledger.protectedBlock(p) && !ledger.claimed(p);
    }
    public boolean observe(BlockPos hit){
        if(contains(hit))return true;
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.isChunkLoaded(hit)
                || !world.getBlockState(hit).isIn(BlockTags.LOGS))return false;
        var found=new LinkedHashSet<BlockPos>();var pending=new ArrayDeque<BlockPos>();
        pending.add(hit.toImmutable());boolean rooted=false,canopy=false;
        var ledger=OwnedBlocks.get(world);
        while(!pending.isEmpty()){
            BlockPos p=pending.removeFirst();
            if(found.contains(p) || !world.isChunkLoaded(p) || !world.getBlockState(p).isIn(BlockTags.LOGS))continue;
            if(found.size()>=160 || p.getSquaredDistance(hit)>40*40)return false;
            if(ledger.protectedBlock(p) || ledger.claimed(p) || world.getBlockEntity(p)!=null)return false;
            found.add(p);
            if(world.isChunkLoaded(p.down()) && world.getBlockState(p.down()).isIn(BlockTags.DIRT))rooted=true;
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                if(x==0 && y==0 && z==0)continue;
                BlockPos q=p.add(x,y,z);if(!world.isChunkLoaded(q))continue;
                var state=world.getBlockState(q);
                if(state.isIn(BlockTags.LOGS) && !found.contains(q))pending.addLast(q);
                if(state.isIn(BlockTags.LEAVES) && (!state.contains(LeavesBlock.PERSISTENT)
                        || !state.get(LeavesBlock.PERSISTENT)))canopy=true;
            }
        }
        if(!rooted || !canopy)return false;
        // Keep several observed remnants when a task or tool change interrupts
        // a tree. Stale coordinates cannot authorize anything protected.
        logs.removeIf(p->world.isChunkLoaded(p) && !world.getBlockState(p).isIn(BlockTags.LOGS));
        while(logs.size()+found.size()>640 && !logs.isEmpty())logs.remove(logs.iterator().next());
        logs.addAll(found);dimension=world.getRegistryKey().getValue().toString();return true;
    }
    public void write(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putInt("Version",1);n.putString("Dimension",dimension);
        n.putLongArray("Logs",logs.stream().mapToLong(BlockPos::asLong).toArray());root.put("TreeHarvest",n);
    }
    public void read(NbtCompound root){
        logs.clear();NbtCompound n=root.getCompound("TreeHarvest");dimension=n.getString("Dimension");
        if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString()))return;
        for(long p:n.getLongArray("Logs")){if(logs.size()>=640)break;logs.add(BlockPos.fromLong(p));}
    }
}
