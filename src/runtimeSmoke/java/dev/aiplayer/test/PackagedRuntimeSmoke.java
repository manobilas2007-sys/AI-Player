package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.brain.DecisionPolicy;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.UUID;

/** Installed only in the isolated CI server. Exercises remapped distributable
 * classes through Fabric/Knot, with no Loom development classpath. */
public final class PackagedRuntimeSmoke implements ModInitializer {
    private static final BlockPos START=new BlockPos(0,100,0),DESTINATION=new BlockPos(8,100,0);
    private CompanionEntity npc;
    private ServerWorld world;
    private Vec3d started,combatStarted;
    private String origin;
    private int ticks,combatAt=-1;
    private boolean routed,done;

    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->guard(server,()->setup(server)));
        ServerTickEvents.END_SERVER_TICK.register(server->guard(server,()->tick(server)));
    }
    private void guard(MinecraftServer server,Checked action){
        if(done)return;
        try{action.run();}catch(Throwable error){
            done=true;
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            System.err.println("PACKAGED_RUNTIME_SMOKE_FAILED\n"+trace);
            try{Files.writeString(java.nio.file.Path.of("smoke-failure.txt"),trace.toString());}
            catch(Exception writing){writing.printStackTrace();}
            server.stop(false);
        }
    }
    private void setup(MinecraftServer server)throws Exception{
        check(!FabricLoader.getInstance().isDevelopmentEnvironment(),"Smoke must use production Fabric, not Loom development mode");
        Class<?> goal=Class.forName("dev.aiplayer.baritone.Goal",true,CompanionEntity.class.getClassLoader());
        var mainOrigin=AIPlayer.class.getProtectionDomain().getCodeSource();
        var goalOrigin=goal.getProtectionDomain().getCodeSource();
        check(mainOrigin!=null && goalOrigin!=null && mainOrigin.getLocation().equals(goalOrigin.getLocation()),
            "Goal must be loaded directly from the same distributable as AIPlayer");
        origin=goalOrigin.getLocation().toExternalForm();
        world=server.getOverworld();
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
        world.setTimeOfDay(18000);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setChunkForced(x,z,true);
        for(int x=-4;x<=24;x++)for(int z=-4;z<=4;z++){
            world.getChunk(new BlockPos(x,99,z));
            world.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(int y=100;y<=103;y++)world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        npc=AIPlayer.COMPANION.create(world);
        check(npc!=null,"Companion registry factory returned null");
        npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=false;npc.autoEquip=false;
        npc.refreshPositionAndAngles(START.getX()+0.5,START.getY(),START.getZ()+0.5,0,0);
        check(world.spawnEntity(npc),"Companion could not spawn in the production world");
        started=npc.getPos();
    }
    private void tick(MinecraftServer server)throws Exception{
        if(npc==null)return;
        ticks++;
        check(npc.isAlive(),"Companion died before completing the packaged runtime test");
        if(ticks==10){
            Path path=WorkNavigation.findPath(npc,DESTINATION,0);
            check(path!=null && WorkNavigation.surfacePath(npc,path,DESTINATION.getY()),
                "Distributable WorkNavigation did not find a real route");
            check(npc.getNavigation().startMovingAlong(path,1.0),"Production navigation rejected the path");
            routed=true;
        }
        if(routed && combatAt<0 && ticks>10
                && npc.squaredDistanceTo(Vec3d.ofBottomCenter(DESTINATION))<1.5){
            check(npc.getPos().squaredDistanceTo(started)>16,"NPC did not physically traverse the planned route");
            NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
            saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
            npc.setMode(Mode.GUARD);
            ZombieEntity hostile=EntityType.ZOMBIE.create(world);
            check(hostile!=null,"Zombie registry factory returned null");
            hostile.setAiDisabled(true);hostile.setPersistent();
            hostile.refreshPositionAndAngles(18.5,100,0.5,0,0);
            check(world.spawnEntity(hostile),"Could not spawn the combat target");
            npc.setTarget(hostile);combatStarted=npc.getPos();combatAt=ticks;
        }
        if(combatAt>=0 && ticks-combatAt>=30){
            check(npc.intelligence().activity()==DecisionPolicy.Activity.COMBAT,
                "NPC did not execute its real combat behavior");
            check(npc.getPos().squaredDistanceTo(combatStarted)>1,
                "Combat did not physically navigate toward the hostile target");
            String loader=FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString();
            String mod=FabricLoader.getInstance().getModContainer("aiplayer").orElseThrow().getMetadata().getVersion().getFriendlyString();
            Files.writeString(java.nio.file.Path.of("smoke-success.txt"),
                "status=passed\nminecraft=1.20.1\nfabric_loader="+loader+"\naiplayer="+mod
                    +"\ncore_origin="+origin+"\nroute_and_combat=physical\nticks="+ticks+"\n");
            done=true;System.out.println("PACKAGED_RUNTIME_SMOKE_PASSED loader="+loader+" mod="+mod);
            server.stop(false);
        }
        check(ticks<240,"Timed out waiting for real navigation/combat in production");
    }
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
}
