package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public final class HouseNaturalTerrainTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c,boolean raised){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
            if(raised && x<12){
                c.setBlockState(new BlockPos(x,2,z),Blocks.DIRT);
                c.setBlockState(new BlockPos(x,3,z),Blocks.GRASS);
            }
        }
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(6,raised?3:2,8));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=true;
        npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,48));
        npc.backpack.setStack(1,new ItemStack(Items.COBBLESTONE,23));
        npc.backpack.setStack(2,new ItemStack(Items.DIAMOND_SHOVEL));
        return npc;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="house_natural_terrain",tickLimit=1300)
    public void preparesVegetationBelowFloorOnARealOneBlockSlope(TestContext c){
        CompanionEntity npc=worker(c,true);
        BlockPos grass=c.getAbsolutePos(new BlockPos(12,2,8));
        c.setBlockState(new BlockPos(13,2,9),Blocks.SNOW);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(new BlockPos(8,3,8)),Items.OAK_PLANKS);
        c.runAtTick(1200,()->{
            c.assertTrue(npc.tasks().progress()>10,"Natural lower grass/snow blocked the entire survey: "+npc.tasks().diagnostics());
            c.assertTrue(c.getWorld().getBlockState(grass).isOf(Blocks.COBBLESTONE)
                && OwnedBlocks.get(c.getWorld()).owned(grass,npc.getUuid()),"Slope did not receive a real owned foundation after vegetation removal");
            c.assertTrue(npc.backpack.count(Items.COBBLESTONE)==0,"Slope foundation did not consume all 23 real blocks (including entrance supports)");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="house_natural_terrain",tickLimit=700)
    public void airborneOrderUsesGroundBelowItsOriginalHorizontalLocation(TestContext c){
        CompanionEntity npc=worker(c,false);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(new BlockPos(8,7,8)),Items.OAK_PLANKS);
        c.runAtTick(600,()->{
            c.assertTrue(npc.tasks().progress()>5,"Airborne order never reached usable ground: "+npc.tasks().diagnostics());
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(10,2,8))).isOf(Blocks.OAK_PLANKS),
                "Airborne order did not keep its original X/Z on ground");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(10,7,8))).isAir(),"Airborne order built a floating floor");
            c.assertTrue(npc.backpack.count(Items.COBBLESTONE)==23,"Flat ground unnecessarily consumed foundation blocks");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="house_natural_terrain",tickLimit=130)
    public void protectedVegetationBelowFloorStillBlocksPreparation(TestContext c){
        CompanionEntity npc=worker(c,true);
        BlockPos protectedGrass=c.getAbsolutePos(new BlockPos(12,2,8));
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),protectedGrass);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(new BlockPos(8,3,8)),Items.OAK_PLANKS);
        c.runAtTick(100,()->{
            c.assertTrue(c.getWorld().getBlockState(protectedGrass).isOf(Blocks.GRASS),"Builder removed protected foundation vegetation");
            c.assertTrue(npc.tasks().progress()==0 && npc.backpack.count(Items.COBBLESTONE)==23
                && npc.backpack.count(Items.OAK_PLANKS)==48,"Blocked preflight consumed project materials");
            npc.tasks().cancel();c.complete();
        });
    }
}
