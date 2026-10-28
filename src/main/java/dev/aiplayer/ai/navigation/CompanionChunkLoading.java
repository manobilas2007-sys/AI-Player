package dev.aiplayer.ai.navigation;

import dev.aiplayer.ai.Mode;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Moving, expiring server tickets keep a working companion independent of the
 * player's simulation distance. Only the current surroundings are requested;
 * distant destinations and hidden resources are never loaded by this service. */
public final class CompanionChunkLoading {
    // Level 30 at the center: 3x3 entity ticking, 5x5 block ticking, 7x7 FULL.
    // Generation dependencies outside FULL are managed by vanilla Minecraft.
    private static final int RADIUS=3, RESUME_GRACE=200;
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create(
        "aiplayer_work",UUID::compareTo,80);
    private static final Map<ServerWorld,WorldWork> WORLDS=new HashMap<>();
    private CompanionChunkLoading(){}

    public static void initialize(){
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity instanceof CompanionEntity npc)state(world).entities.put(npc.getUuid(),npc);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            if(!(entity instanceof CompanionEntity npc))return;
            WorldWork state=WORLDS.get(world);if(state==null)return;
            state.entities.remove(npc.getUuid());
            Entity.RemovalReason reason=npc.getRemovalReason();
            if(reason!=null && (reason.shouldDestroy() || reason==Entity.RemovalReason.CHANGED_DIMENSION))
                state.forget(world,npc.getUuid());
            else state.release(world,npc.getUuid());
        });
        ServerTickEvents.START_WORLD_TICK.register(CompanionChunkLoading::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->close(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            for(ServerWorld world:server.getWorlds())close(world);
        });
    }

    private static WorldWork state(ServerWorld world){
        return WORLDS.computeIfAbsent(world,w->new WorldWork(w.getPersistentStateManager()
            .getOrCreate(SavedWork::read,SavedWork::new,"aiplayer_active_chunks")));
    }
    private static boolean working(CompanionEntity npc){
        return npc.isAlive() && !npc.isRemoved() && npc.ownerId()!=null
            && (npc.tasks().active() || npc.gathering().active() || npc.gathering().hasCleanup()
                || npc.crafting().hasSession() || npc.intelligence().restoration().pending()
                || (npc.mode()!=Mode.WAIT && !npc.getNavigation().isIdle()));
    }
    private static void tick(ServerWorld world){
        WorldWork state=state(world);
        long now=world.getServer().getTicks();
        // Saved projects can wake their own last chunk after a server restart.
        // Missing/deleted entities get a bounded grace period, not a permanent load.
        for(var entry:Map.copyOf(state.saved.positions).entrySet()){
            UUID id=entry.getKey();if(state.entities.containsKey(id))continue;
            long deadline=state.pending.computeIfAbsent(id,key->now+RESUME_GRACE);
            if(now>deadline){state.forget(world,id);continue;}
            state.hold(world,id,entry.getValue());
        }
        for(var entry:Map.copyOf(state.entities).entrySet()){
            UUID id=entry.getKey();CompanionEntity npc=entry.getValue();
            state.pending.remove(id);
            if(npc.getWorld()!=world || !working(npc)){state.forget(world,id);continue;}
            state.hold(world,id,npc.getChunkPos());
        }
        // Vanilla otherwise suspends entities after 300 empty-server ticks,
        // even when an ordinary non-player ticket keeps their chunk ticking.
        if(!state.tickets.isEmpty())world.resetIdleTimeout();
    }
    private static void close(ServerWorld world){
        WorldWork state=WORLDS.remove(world);if(state==null)return;
        for(UUID id:Map.copyOf(state.tickets).keySet())state.release(world,id);
        state.entities.clear();state.pending.clear();
    }

    /** Read-only diagnostics for status and lifecycle regression tests. */
    public static ChunkPos loadedCenter(CompanionEntity npc){
        if(!(npc.getWorld() instanceof ServerWorld world))return null;
        WorldWork state=WORLDS.get(world);return state==null?null:state.tickets.get(npc.getUuid());
    }

    private static final class WorldWork {
        final SavedWork saved;
        final Map<UUID,CompanionEntity> entities=new HashMap<>();
        final Map<UUID,ChunkPos> tickets=new HashMap<>();
        final Map<UUID,Long> pending=new HashMap<>();
        WorldWork(SavedWork saved){this.saved=saved;}
        void hold(ServerWorld world,UUID id,ChunkPos center){
            ChunkPos previous=tickets.put(id,center);
            // Renew before releasing the previous center to retain overlapping
            // ticking regions while crossing a chunk boundary.
            world.getChunkManager().addTicket(TICKET,center,RADIUS,id);
            if(previous!=null && !previous.equals(center))
                world.getChunkManager().removeTicket(TICKET,previous,RADIUS,id);
            if(!center.equals(saved.positions.put(id,center)))saved.markDirty();
        }
        void release(ServerWorld world,UUID id){
            ChunkPos center=tickets.remove(id);
            if(center!=null)world.getChunkManager().removeTicket(TICKET,center,RADIUS,id);
        }
        void forget(ServerWorld world,UUID id){
            release(world,id);pending.remove(id);
            if(saved.positions.remove(id)!=null)saved.markDirty();
        }
    }
    private static final class SavedWork extends PersistentState {
        final Map<UUID,ChunkPos> positions=new HashMap<>();
        static SavedWork read(NbtCompound nbt){
            SavedWork state=new SavedWork();
            NbtList list=nbt.getList("Workers",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<list.size();i++){
                NbtCompound entry=list.getCompound(i);
                if(entry.containsUuid("Id"))state.positions.put(entry.getUuid("Id"),
                    new ChunkPos(entry.getInt("X"),entry.getInt("Z")));
            }
            return state;
        }
        @Override public NbtCompound writeNbt(NbtCompound nbt){
            NbtList list=new NbtList();
            positions.forEach((id,pos)->{
                NbtCompound entry=new NbtCompound();entry.putUuid("Id",id);
                entry.putInt("X",pos.x);entry.putInt("Z",pos.z);list.add(entry);
            });
            nbt.put("Workers",list);return nbt;
        }
    }
}
