package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public final class RemoteProjectTests implements FabricGameTest {
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="remote_project_return",tickLimit=4000)
    public void finishesSavedMaterialDependencyAndPhysicallyReturnsToDistantHouse(TestContext c){
        BlockPos origin=c.getAbsolutePos(new BlockPos(20000,2,20000));
        for(int x=-2;x<=332;x++)for(int z=-3;z<=10;z++){
            c.getWorld().setBlockState(origin.add(x,-1,z),Blocks.DIRT.getDefaultState());
            for(int y=0;y<4;y++)c.getWorld().setBlockState(origin.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        CompanionEntity npc=AIPlayer.COMPANION.create(c.getWorld());
        npc.refreshPositionAndAngles(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        NbtCompound owner=new NbtCompound();npc.writeCustomDataToNbt(owner);owner.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(owner);
        npc.autoCraft=true;npc.autoEquip=true;npc.autoLoot=true;npc.autoSurvive=false;npc.setMode(dev.aiplayer.ai.Mode.WAIT);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,origin,net.minecraft.item.Items.OAK_PLANKS);
        NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
        saved.getCompound("DetailedHouse").putString("Phase","BUILD");
        saved.getCompound("ProjectMaterials").putString("PendingItem","minecraft:oak_planks");
        saved.getCompound("ProjectMaterials").putInt("PendingAmount",1);
        npc.refreshPositionAndAngles(origin.getX()+320.5,origin.getY(),origin.getZ()+0.5,0,0);
        npc.backpack.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_LOG));
        npc.tasks().readNbt(saved);c.getWorld().spawnEntity(npc);
        BlockPos floor=origin.east(2);
        c.runAtEveryTick(()->{
            if(!c.getWorld().isChunkLoaded(floor) || !c.getWorld().getBlockState(floor).isOf(Blocks.OAK_PLANKS))return;
            c.assertTrue(npc.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(floor))<25,"Return placed a block remotely");
            c.assertTrue(npc.backpack.count(net.minecraft.item.Items.OAK_LOG)==0,"Saved material prerequisite was not crafted");
            c.assertTrue(dev.aiplayer.ai.OwnedBlocks.get(c.getWorld()).owned(floor,npc.getUuid()),"Returned builder did not claim its physical floor");
            npc.discard();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="remote_project",tickLimit=220)
    public void startsWalkingTowardRememberedUnloadedDestinationWithoutReadingIt(TestContext c){
        for(int x=0;x<40;x++)for(int z=0;z<12;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(3,2,5));
        npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=false;
        ProjectWork work=new ProjectWork(npc);double initial=npc.getX();
        BlockPos destination=npc.getBlockPos().east(4096);
        c.assertFalse(c.getWorld().isChunkLoaded(destination),"Remote fixture destination was already loaded");
        c.runAtEveryTick(()->{
            c.assertFalse(work.moveTo(destination),"Intermediate segment reported final arrival");
            c.assertFalse(c.getWorld().isChunkLoaded(destination),"Routing synchronously loaded its distant destination");
        });
        c.runAtTick(180,()->{
            c.assertTrue(npc.getX()>initial+8,"Unloaded remembered destination prevented physical local travel: "+work.diagnostics());
            c.assertTrue(npc.getX()<initial+40,"Local segment skipped physical travel");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="remote_project",tickLimit=40)
    public void restoresHouseAndFarmWhileWorkerIsFarFromTheirOriginalSites(TestContext c){
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(3,2,5));
        BlockPos site=npc.getBlockPos();
        for(CompanionTasks.Kind kind:new CompanionTasks.Kind[]{CompanionTasks.Kind.BUILD_HOUSE,CompanionTasks.Kind.CREATE_FARM}){
            npc.refreshPositionAndAngles(site.getX()+0.5,site.getY(),site.getZ()+0.5,0,0);
            npc.tasks().startAt(kind,site,null);
            NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
            // Fixture placement simulates a saved worker returning from a distant resource trip.
            npc.refreshPositionAndAngles(site.getX()+320.5,site.getY(),site.getZ()+0.5,0,0);
            npc.tasks().readNbt(saved);
            c.assertTrue(npc.tasks().active() && npc.tasks().kind()==kind,"Distant saved project was discarded: "+kind);
            NbtCompound restored=new NbtCompound();npc.tasks().writeNbt(restored);
            c.assertTrue(restored.getCompound("CompanionProject").getLong("Anchor")==saved.getCompound("CompanionProject").getLong("Anchor"),
                "Restoring a distant project relocated its original site");
        }
        npc.discard();c.complete();
    }
}
