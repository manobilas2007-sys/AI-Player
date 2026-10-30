package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.brain.DecisionPolicy;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Project ingredients must reach their own dependency executor rather than
 * being reserved by the generic starter-tool planner on the next brain tick. */
public final class ProjectArbitrationTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            c.setBlockState(new BlockPos(x,1,z),Blocks.GRASS_BLOCK);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
        }
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(6,2,8));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
        npc.backpack.clear();npc.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        npc.setMode(Mode.WAIT);npc.autoCraft=true;npc.autoSurvive=false;npc.autoLoot=true;
        return npc;
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_arbitration",tickLimit=100)
    public void shelterKeepsItsFirstLogWhilePreparingGrassInsteadOfStartingGenericTools(TestContext c){
        CompanionEntity npc=worker(c);npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG));
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(new BlockPos(12,2,12)),Items.OAK_PLANKS);
        c.runAtTick(5,()->{
            c.assertTrue(npc.intelligence().activity()==DecisionPolicy.Activity.PROJECT,
                "First project log was diverted to generic crafting: "+npc.intelligence().status());
            c.assertFalse(npc.crafting().hasSession(),"Generic tool crafting reserved the project's first log");
            c.assertTrue(npc.backpack.count(Items.OAK_LOG)==1,"Generic crafting consumed the project log before terrain preparation");
        });
        c.runAtTick(80,()->{
            c.assertTrue(npc.tasks().active(),"Unfunded shelter incorrectly completed");
            c.assertFalse(npc.crafting().hasSession(),"Grass around the worker trapped it in generic bench work");
            c.assertTrue(npc.backpack.count(Items.STICK)==0 && npc.backpack.count(Items.CRAFTING_TABLE)==0,
                "Generic tool prerequisites consumed the shelter's wood reserve");
            npc.tasks().cancel();c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_arbitration",tickLimit=90)
    public void farmPreservesBucketIronWhileItsSiteIsBlocked(TestContext c){
        CompanionEntity npc=worker(c);
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,3));
        npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,3));
        npc.backpack.setStack(2,new ItemStack(Items.STICK,4));
        npc.backpack.setStack(3,new ItemStack(Items.CRAFTING_TABLE));
        BlockPos origin=c.getAbsolutePos(new BlockPos(12,2,12)),blocked=origin.add(2,-1,0);
        c.getWorld().setBlockState(blocked,Blocks.BRICKS.getDefaultState());
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),blocked);
        npc.tasks().startAt(CompanionTasks.Kind.CREATE_FARM,origin,null);
        c.runAtTick(60,()->{
            c.assertTrue(npc.tasks().active() && npc.intelligence().activity()==DecisionPolicy.Activity.PROJECT,
                "Blocked farm lost ownership of its ingredient plan: "+npc.intelligence().status());
            c.assertTrue(npc.backpack.count(Items.IRON_INGOT)==3 && npc.backpack.count(Items.IRON_AXE)==0,
                "Generic axe maintenance consumed iron reserved for the farm bucket");
            c.assertTrue(c.getWorld().getBlockState(blocked).isOf(Blocks.BRICKS),"Farm modified its protected obstruction");
            c.assertFalse(npc.crafting().hasSession(),"Farm started an unrelated generic crafting session");
            npc.tasks().cancel();c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_arbitration",tickLimit=90)
    public void existingRecipeFinishesOnceThenReturnsToTheConfirmedProject(TestContext c){
        CompanionEntity npc=worker(c);npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG));
        npc.crafting().tick();
        c.assertTrue(npc.crafting().hasPendingRecipe(),"Fixture failed to begin its real plank recipe");
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,c.getAbsolutePos(new BlockPos(12,2,12)),null);
        c.runAtTick(50,()->{
            c.assertTrue(npc.backpack.count(Items.OAK_LOG)==0 && npc.backpack.count(Items.OAK_PLANKS)==4,
                "Existing recipe was lost or chained into unrelated tools");
            c.assertTrue(npc.backpack.count(Items.STICK)==0 && npc.backpack.count(Items.CRAFTING_TABLE)==0,
                "Completed pending recipe queued a new generic dependency");
            c.assertFalse(npc.crafting().hasPendingRecipe(),"Pending recipe never released the confirmed project");
            c.assertTrue(npc.tasks().active() && npc.intelligence().activity()==DecisionPolicy.Activity.PROJECT,
                "Project did not resume after the already pending recipe completed: "+npc.intelligence().status());
            npc.tasks().cancel();c.complete();
        });
    }
}
