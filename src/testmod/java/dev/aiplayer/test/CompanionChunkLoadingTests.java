package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.navigation.CompanionChunkLoading;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

public final class CompanionChunkLoadingTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c,BlockPos pos){
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)
            c.getWorld().setBlockState(pos.add(x,-1,z),Blocks.STONE.getDefaultState());
        CompanionEntity npc=AIPlayer.COMPANION.create(c.getWorld());
        npc.refreshPositionAndAngles(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        NbtCompound data=new NbtCompound();npc.writeCustomDataToNbt(data);
        data.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(data);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,pos,null);
        c.getWorld().spawnEntity(npc);return npc;
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="companion_chunk_loading",tickLimit=2000)
    public void distantWorkerAndFurnaceTickWithoutAPlayerOrPermanentForceload(TestContext c){
        // Deliberately outside spawn chunks, the test arena and every player.
        BlockPos pos=new BlockPos(8192,100,8192);
        var before=new java.util.HashSet<Long>(c.getWorld().getForcedChunks());
        CompanionEntity npc=worker(c,pos);
        BlockPos oven=pos.add(1,0,0);
        c.getWorld().setBlockState(oven,Blocks.FURNACE.getDefaultState());
        FurnaceBlockEntity furnace=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oven);
        furnace.setStack(0,new ItemStack(Items.RAW_IRON));furnace.setStack(1,new ItemStack(Items.COAL));
        int[] age={-1};long[] ready={-1};
        c.runAtEveryTick(()->{
            // Ticket loading/generation is asynchronous: measure ticking after
            // the first real entity tick, never against a fixed startup delay.
            if(ready[0]<0){if(npc.age>0){ready[0]=c.getTick();age[0]=npc.age;}return;}
            if(c.getTick()-ready[0]<280)return;
            c.assertTrue(c.getWorld().getPlayers().stream().noneMatch(p->p.squaredDistanceTo(npc)<256*256),"Fixture has a nearby player");
            c.assertTrue(npc.getChunkPos().equals(CompanionChunkLoading.loadedCenter(npc)),"Worker has no moving ticket");
            c.assertTrue(npc.age>=age[0]+250,"Distant worker stopped after chunk activation: initial="+age[0]+" age="+npc.age);
            c.assertTrue(furnace.getStack(2).isOf(Items.IRON_INGOT),"Distant vanilla furnace stopped ticking");
            c.assertTrue(c.getWorld().getForcedChunks().equals(before),"Worker added a permanent forced chunk");
            npc.tasks().cancel();npc.discard();c.complete();
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="companion_chunk_loading",tickLimit=200)
    public void ticketFollowsChunkCrossingAndReleasesWhenWorkIsCancelled(TestContext c){
        BlockPos pos=new BlockPos(8704,100,8192);CompanionEntity npc=worker(c,pos);
        ChunkPos first=new ChunkPos(pos);BlockPos next=pos.add(16,0,0);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().setBlockState(next.add(x,-1,z),Blocks.STONE.getDefaultState());
        c.runAtTick(40,()->{
            c.assertTrue(first.equals(CompanionChunkLoading.loadedCenter(npc)),"Initial work ticket missing");
            // Isolate ticket ownership at a boundary; navigation itself has
            // separate physical journey tests.
            npc.refreshPositionAndAngles(next.getX()+0.5,next.getY(),next.getZ()+0.5,0,0);
        });
        c.runAtTick(60,()->{
            c.assertTrue(new ChunkPos(next).equals(CompanionChunkLoading.loadedCenter(npc)),"Ticket remained at the departed chunk");
            npc.tasks().cancel();npc.gathering().cancel();npc.getNavigation().stop();
        });
        c.runAtTick(90,()->{
            c.assertTrue(CompanionChunkLoading.loadedCenter(npc)==null,"Cancelled worker retained a work ticket");
            npc.discard();c.complete();
        });
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="companion_chunk_loading",tickLimit=140)
    public void removalReleasesOnlyThatWorkersTicket(TestContext c){
        BlockPos pos=new BlockPos(9216,100,8192);
        CompanionEntity first=worker(c,pos),second=worker(c,pos.add(0,0,2));
        c.runAtTick(40,()->{
            c.assertTrue(CompanionChunkLoading.loadedCenter(first)!=null && CompanionChunkLoading.loadedCenter(second)!=null,"Workers did not acquire independent tickets");
            first.discard();
        });
        c.runAtTick(70,()->{
            c.assertTrue(CompanionChunkLoading.loadedCenter(first)==null,"Removed worker retained its ticket");
            c.assertTrue(CompanionChunkLoading.loadedCenter(second)!=null,"Removing one worker cancelled the other's ticket");
            second.discard();c.complete();
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="companion_chunk_loading",tickLimit=2000)
    public void standaloneRecipeKeepsItsChunkUntilPhysicalCraftingFinishes(TestContext c){
        BlockPos pos=new BlockPos(9728,100,8192);CompanionEntity npc=worker(c,pos);
        npc.tasks().cancel();npc.setMode(Mode.FOLLOW);npc.autoEquip=false;
        npc.backpack.clear();npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG));
        npc.autoCraft=true;npc.crafting().tick();
        c.assertTrue(npc.crafting().hasPendingRecipe(),"Fixture did not start a real crafting session");
        npc.autoCraft=false;npc.opened();
        long[] ready={-1};boolean[] resumed={false};
        c.runAtEveryTick(()->{
            if(ready[0]<0){if(npc.age>0)ready[0]=c.getTick();return;}
            if(c.getTick()-ready[0]<80)return;
            if(!resumed[0]){
                c.assertTrue(!npc.tasks().active() && !npc.gathering().active() && npc.getNavigation().isIdle(),
                    "Another task kept the crafting fixture loaded");
                c.assertTrue(npc.crafting().hasPendingRecipe() && CompanionChunkLoading.loadedCenter(npc)!=null,
                    "Standalone paused recipe lost its chunk ticket");
                npc.closed();resumed[0]=true;return;
            }
            if(c.getTick()-ready[0]<150)return;
            c.assertTrue(npc.backpack.count(Items.OAK_LOG)==0 && npc.backpack.count(Items.OAK_PLANKS)==4,
                "Resumed real recipe did not consume its log and produce four planks: age="+npc.age+" brain="+npc.intelligence().status());
            c.assertTrue(!npc.crafting().hasSession() && CompanionChunkLoading.loadedCenter(npc)==null,
                "Completed standalone recipe retained a chunk ticket");
            npc.discard();c.complete();
        });
    }
}
