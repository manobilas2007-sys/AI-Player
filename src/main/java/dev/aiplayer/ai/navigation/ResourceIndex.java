/*
 * Server adaptation of Baritone (c) leijurv and contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Upstream: 8c55ad0721d42a01a359834edc16aaa9b5bc587d (1.10.5).
 * NPC changes: server entity movement/physics, bounded scanning, actual
 * inventory, task ownership and persistence. See embedded-baritone/UPSTREAM.md.
 */
package dev.aiplayer.ai.navigation;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import java.util.*;
import java.util.function.Predicate;

/** Incremental server adaptation of Baritone WorldScanner/CachedWorld.
 * Reads only resident chunk sections, including occluded resources. Remembered
 * coordinates survive unloading; interaction always revalidates the live block.
 * The moving NPC tickets, not this scanner, load the next travelling segment. */
public final class ResourceIndex {
    private static final int CELLS_PER_TICK=1024, MAX_PER_ITEM=512;
    private record Section(int x,int y,int z) {}
    private final CompanionEntity npc;
    private final Map<Item,LinkedHashSet<BlockPos>> known=new LinkedHashMap<>();
    private final LinkedHashSet<Long> visited=new LinkedHashSet<>();
    private final ArrayDeque<Section> sections=new ArrayDeque<>();
    private Item scanning;
    private Section section;
    private BlockPos origin;
    private int offset,refreshAt,lastTick=-1,scanned;
    public ResourceIndex(CompanionEntity npc){this.npc=npc;}
    public int scannedThisTick(){return lastTick==npc.age?scanned:0;}
    public boolean scanComplete(Item item){return scanning==item && origin!=null && section==null && sections.isEmpty()
        && origin.getSquaredDistance(npc.getBlockPos())<=16*16;}
    public int remembered(Item item){return known.getOrDefault(item,new LinkedHashSet<>()).size();}
    public void remember(Item item,BlockPos pos){
        if(item==Items.AIR)return;
        var entries=known.computeIfAbsent(item,k->new LinkedHashSet<>());
        if(entries.contains(pos))return;
        if(entries.size()>=MAX_PER_ITEM){
            BlockPos farthest=entries.stream().max(Comparator.comparingDouble(this::retentionCost)).orElseThrow();
            if(retentionCost(pos)>=retentionCost(farthest))return;
            entries.remove(farthest);
        }
        entries.add(pos.toImmutable());
    }
    private double retentionCost(BlockPos p){
        double distance=p.getSquaredDistance(npc.getBlockPos());
        if(!npc.getWorld().isChunkLoaded(p))return distance;
        for(Direction side:Direction.values())if(npc.getWorld().getBlockState(p.offset(side)).isAir())return distance;
        return distance+256;
    }
    public void forget(Item item,BlockPos pos){var entries=known.get(item);if(entries!=null)entries.remove(pos);}
    public static boolean matches(Item item,BlockState s){
        if(item==Items.COBBLESTONE)return s.isOf(Blocks.STONE);
        if(item==Items.COAL)return s.isOf(Blocks.COAL_ORE)||s.isOf(Blocks.DEEPSLATE_COAL_ORE);
        if(item==Items.RAW_IRON)return s.isOf(Blocks.IRON_ORE)||s.isOf(Blocks.DEEPSLATE_IRON_ORE);
        if(item==Items.DIRT)return s.isOf(Blocks.DIRT)||s.isOf(Blocks.GRASS_BLOCK);
        return item instanceof BlockItem block && s.isOf(block.getBlock());
    }
    public boolean visited(BlockPos pos){return visited.contains(new ChunkPos(pos).toLong());}
    public void visit(BlockPos pos){
        visited.add(new ChunkPos(pos).toLong());while(visited.size()>2048)visited.remove(visited.iterator().next());
    }
    private void queue(ServerWorld world,Item item){
        scanning=item;origin=npc.getBlockPos().toImmutable();sections.clear();section=null;offset=0;refreshAt=npc.age+200;
        ChunkPos here=new ChunkPos(origin);var candidates=new ArrayList<Section>();
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            WorldChunk chunk=world.getChunkManager().getWorldChunk(here.x+dx,here.z+dz);
            if(chunk==null)continue;
            ChunkSection[] parts=chunk.getSectionArray();
            for(int i=0;i<parts.length;i++)if(!parts[i].isEmpty() && parts[i].hasAny(s->matches(item,s)))
                candidates.add(new Section((here.x+dx)*16,world.sectionIndexToCoord(i)*16,(here.z+dz)*16));
        }
        candidates.sort(Comparator.comparingDouble(s->new BlockPos(s.x+8,s.y+8,s.z+8).getSquaredDistance(origin)));
        sections.addAll(candidates);
    }
    public void scan(Item item){
        if(!(npc.getWorld() instanceof ServerWorld world))return;
        if(lastTick!=npc.age){lastTick=npc.age;scanned=0;}
        if(scanned>=CELLS_PER_TICK)return;
        if(scanning!=item || origin==null || origin.getSquaredDistance(npc.getBlockPos())>16*16
                || (section==null && sections.isEmpty() && npc.age>=refreshAt))queue(world,item);
        while(scanned<CELLS_PER_TICK){
            if(section==null){section=sections.poll();offset=0;if(section==null)return;}
            WorldChunk chunk=world.getChunkManager().getWorldChunk(section.x>>4,section.z>>4);
            if(chunk==null){section=null;continue;}
            int i=offset++;BlockPos p=new BlockPos(section.x+(i&15),section.y+(i>>8),section.z+((i>>4)&15));
            scanned++;if(matches(item,chunk.getBlockState(p))){
                boolean exposed=false;
                for(Direction side:Direction.values())if(world.isChunkLoaded(p.offset(side)) && world.getBlockState(p.offset(side)).getCollisionShape(world,p.offset(side)).isEmpty()){exposed=true;break;}
                if(exposed || (i&127)==0 || item!=Items.COBBLESTONE && item!=Items.DIRT)remember(item,p);
            }
            if(offset==4096)section=null;
        }
    }
    /** Cost includes horizontal travel, vertical effort and return travel to
     * the worksite. Only the best live candidates proceed to path calculation. */
    public List<BlockPos> candidates(Item item,BlockPos returnTo,Predicate<BlockPos> allowed){
        scan(item);var world=npc.getWorld();var entries=known.get(item);if(entries==null)return List.of();
        entries.removeIf(p->world.isChunkLoaded(p) && !matches(item,world.getBlockState(p)));
        return entries.stream().filter(allowed).sorted(Comparator.comparingDouble(p->{
            double outward=Math.sqrt(p.getSquaredDistance(npc.getBlockPos()));
            return outward+Math.abs(p.getY()-npc.getBlockY())*2
                +(returnTo==null?0:Math.sqrt(p.getSquaredDistance(returnTo))*0.35);
        })).limit(16).toList();
    }
    public void write(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putInt("Version",1);
        n.putString("Dimension",npc.getWorld().getRegistryKey().getValue().toString());
        NbtCompound resources=new NbtCompound();known.forEach((item,positions)->resources.putLongArray(Registries.ITEM.getId(item).toString(),positions.stream().mapToLong(BlockPos::asLong).toArray()));
        n.put("Resources",resources);n.putLongArray("Visited",visited.stream().mapToLong(Long::longValue).toArray());root.put("ResourceIndex",n);
    }
    public void read(NbtCompound root){
        known.clear();visited.clear();sections.clear();section=null;origin=null;scanning=null;lastTick=-1;
        NbtCompound n=root.getCompound("ResourceIndex");
        if(n.getInt("Version")!=1 || !n.getString("Dimension").equals(npc.getWorld().getRegistryKey().getValue().toString()))return;
        NbtCompound resources=n.getCompound("Resources");
        for(String key:resources.getKeys()){
            Identifier id=Identifier.tryParse(key);if(id==null)continue;Item item=Registries.ITEM.get(id);
            int count=0;for(long pos:resources.getLongArray(key)){if(count++>=MAX_PER_ITEM)break;remember(item,BlockPos.fromLong(pos));}
        }
        for(long chunk:n.getLongArray("Visited")){if(visited.size()>=2048)break;visited.add(chunk);}
    }
}
