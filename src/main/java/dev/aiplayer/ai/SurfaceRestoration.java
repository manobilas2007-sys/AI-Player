package dev.aiplayer.ai;

import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Durable obligation owned by the NPC, surviving project cancellation. Only
 * surface cells actually removed by its stair are capped; the tunnel stays open. */
public final class SurfaceRestoration {
    private final CompanionEntity npc;
    private final ProjectWork work;
    private BlockPos exit;
    private final LinkedHashSet<BlockPos> caps=new LinkedHashSet<>();
    private String dimension="";
    private boolean returning,arrived,coverSurface;
    private int lastTick=-1;
    public SurfaceRestoration(CompanionEntity npc){this.npc=npc;work=new ProjectWork(npc);}
    public boolean pending(){return returning && opened();}
    public boolean opened(){return exit!=null && dimension.equals(npc.getWorld().getRegistryKey().getValue().toString());}
    public void begin(BlockPos entry){if(!opened()){caps.clear();returning=false;arrived=false;exit=entry.toImmutable();coverSurface=npc.getWorld().isSkyVisible(entry);dimension=npc.getWorld().getRegistryKey().getValue().toString();}}
    public void removed(BlockPos p){if(exit!=null && coverSurface && p.getY()==exit.getY()-1 && p.getSquaredDistance(exit)<=64 && caps.size()<32)caps.add(p.toImmutable());}
    public void requestReturn(){if(exit!=null)returning=true;}
    public void pause(){work.pause();}
    public String diagnostics(){return "returning="+returning+" exit="+exit+" caps="+caps+" work={"+work.diagnostics()+"}";}
    public boolean tick(){
        if(!pending())return false;
        if(lastTick==npc.age)return true;lastTick=npc.age;
        if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString()))return false;
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return true;}
        if(!arrived){if(!work.moveTo(exit))return true;arrived=true;}
        ServerWorld world=(ServerWorld)npc.getWorld();
        while(!caps.isEmpty()){
            BlockPos p=caps.iterator().next();
            if(!world.isChunkLoaded(p))return true;
            // A later player edit takes precedence; never replace it.
            if(!world.getBlockState(p).isAir() || work.protectedPosition(p)){caps.remove(p);continue;}
            if(npc.backpack.count(Items.DIRT)==0 && !npc.getMainHandStack().isOf(Items.DIRT)){
                var drops=world.getEntitiesByClass(ItemEntity.class,npc.getBoundingBox().expand(8),
                    item->item.isAlive() && item.getStack().isOf(Items.DIRT) && !item.cannotPickup());
                if(!drops.isEmpty()){WorkNavigation.moveToDrop(npc,drops.get(0),1);return true;}
                // Obtain real replacement soil without undermining the exit,
                // reopening a cap or exposing an adjacent liquid.
                for(BlockPos soil:BlockPos.iterate(exit.add(-3,-1,-3),exit.add(3,-1,3))){
                    if(caps.contains(soil) || soil.equals(exit.down()) || !world.isChunkLoaded(soil)
                        || !(world.getBlockState(soil).isOf(Blocks.DIRT) || world.getBlockState(soil).isOf(Blocks.GRASS_BLOCK)))continue;
                    boolean dry=true;for(Direction d:Direction.values())if(!world.isChunkLoaded(soil.offset(d)) || !world.getFluidState(soil.offset(d)).isEmpty())dry=false;
                    if(dry && !work.protectedPosition(soil)){work.safeMine(soil.toImmutable());return true;}
                }
                return true;
            }
            if(work.place(p,Blocks.DIRT.getDefaultState(),Items.DIRT,1))caps.remove(p);
            return true;
        }
        exit=null;returning=false;arrived=false;dimension="";work.pause();return false;
    }
    public void write(NbtCompound root){
        if(exit==null)return;NbtCompound n=new NbtCompound();n.putLong("Exit",exit.asLong());n.putString("Dimension",dimension);
        n.putBoolean("Returning",returning);n.putBoolean("CoverSurface",coverSurface);n.putLongArray("Caps",caps.stream().mapToLong(BlockPos::asLong).toArray());root.put("SurfaceRestoration",n);
    }
    public void read(NbtCompound root){
        exit=null;caps.clear();returning=false;arrived=false;lastTick=-1;
        NbtCompound n=root.getCompound("SurfaceRestoration");if(!n.contains("Exit"))return;
        dimension=n.getString("Dimension");if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString()))return;
        exit=BlockPos.fromLong(n.getLong("Exit"));returning=n.getBoolean("Returning");coverSurface=n.getBoolean("CoverSurface");
        for(long packed:n.getLongArray("Caps")){BlockPos p=BlockPos.fromLong(packed);
            if(caps.size()<32 && p.getY()==exit.getY()-1 && p.getSquaredDistance(exit)<=64)caps.add(p);}
    }
}
