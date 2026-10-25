package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Confirmed agricultural work uses the coordinator and real world/inventory. */
public final class FarmProjectTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(15,2,15));
        NbtCompound n=new NbtCompound();npc.writeCustomDataToNbt(n);n.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(n);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=true;
        return npc;
    }
    private BlockPos protectedFarmland(TestContext c,BlockPos relative){
        BlockPos soil=c.getAbsolutePos(relative);
        c.getWorld().setBlockState(soil,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),soil);return soil;
    }
    private int count(CompanionEntity npc,net.minecraft.item.Item item){return npc.backpack.count(item)
        +(npc.getMainHandStack().isOf(item)?npc.getMainHandStack().getCount():0);}

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="farm_player_tilled",tickLimit=250)
    public void confirmedCarePlantsPlayerTilledSoilWithoutNeedingAHoe(TestContext c){
        CompanionEntity npc=worker(c);BlockPos soil=protectedFarmland(c,new BlockPos(18,1,15));
        npc.backpack.setStack(0,new ItemStack(Items.WHEAT_SEEDS));
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        c.runAtTick(190,()->{
            c.assertTrue(c.getWorld().getBlockState(soil.up()).isOf(Blocks.WHEAT),
                "Confirmed care ignored manually tilled farmland: "+npc.tasks().status());
            c.assertTrue(count(npc,Items.WHEAT_SEEDS)==0,"Planting did not consume the real seed");
            c.assertTrue(OwnedBlocks.get(c.getWorld()).protectedBlock(soil),"Care erased the player's soil provenance");
            c.assertTrue(count(npc,Items.WOODEN_HOE)==0 && count(npc,Items.IRON_HOE)==0,"Care fabricated an unnecessary hoe");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="farm_existing_harvest",tickLimit=600)
    public void confirmedCarePhysicallyCollectsMatureCropAndPreservesYoungCrop(TestContext c){
        CompanionEntity npc=worker(c);BlockPos mature=protectedFarmland(c,new BlockPos(18,1,15));
        BlockPos young=protectedFarmland(c,new BlockPos(19,1,15));
        c.getWorld().setBlockState(mature.up(),((CropBlock)Blocks.WHEAT).withAge(7));
        c.getWorld().setBlockState(young.up(),((CropBlock)Blocks.WHEAT).withAge(1));
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),mature.up());
        npc.backpack.setStack(0,new ItemStack(Items.WHEAT_SEEDS));Vec3d initial=npc.getPos();
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        c.runAtTick(480,()->{
            c.assertTrue(count(npc,Items.WHEAT)==1,"Existing mature wheat was not physically harvested: "+npc.tasks().status());
            c.assertTrue(npc.getPos().squaredDistanceTo(initial)>0.25,"Care remotely inserted distant harvest drops");
            BlockStateCheck.replanted(c,mature.up());
            c.assertTrue(c.getWorld().getBlockState(young.up()).isOf(Blocks.WHEAT),"Care destroyed an immature crop");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="farm_radius_persistence",tickLimit=60)
    public void careKeepsOriginalHorizontalRadiusAndRejectsOtherDimension(TestContext c){
        CompanionEntity npc=worker(c);BlockPos original=npc.getBlockPos();
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,original,null);
        NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
        BlockPos boundary=original.add(100,-1,0),outside=original.add(101,-1,0);
        saved.getCompound("FarmProject").putLongArray("Fields",new long[]{boundary.asLong(),outside.asLong()});
        npc.refreshPositionAndAngles(original.getX()+8.5,original.getY(),original.getZ()+0.5,0,0);
        npc.tasks().readNbt(saved);NbtCompound restored=new NbtCompound();npc.tasks().writeNbt(restored);
        c.assertTrue(restored.getCompound("FarmProject").getLong("Origin")==original.asLong(),"Moving the owner/worker changed the confirmed origin");
        long[] fields=restored.getCompound("FarmProject").getLongArray("Fields");
        c.assertTrue(fields.length==1 && fields[0]==boundary.asLong(),"Care dropped the inclusive horizontal 100-block edge or accepted 101 blocks");
        saved.getCompound("FarmProject").putString("Dimension","minecraft:the_nether");
        npc.tasks().readNbt(saved);c.assertFalse(npc.tasks().active(),"Care resumed original coordinates in another dimension");
        c.complete();
    }
    private void river(TestContext c){
        for(int x:new int[]{8,11,14})c.setBlockState(new BlockPos(x,1,12),Blocks.WATER);
    }
    private int wheat(TestContext c,BlockPos anchor){
        int count=0;for(int x=1;x<6;x++)for(int z=1;z<6;z++)if(c.getWorld().getBlockState(anchor.add(x,1,z)).isOf(Blocks.WHEAT))count++;
        return count;
    }
    private void finished(TestContext c,CompanionEntity npc,BlockPos anchor,int seeds){
        c.assertFalse(npc.tasks().active(),"Farm construction did not finish: "+npc.tasks().diagnostics()+" pos="+npc.getPos());
        c.assertTrue(npc.mode()==Mode.FOLLOW,"Completed farm did not release the NPC to following mode");
        c.assertTrue(count(npc,Items.BUCKET)==1 && count(npc,Items.WATER_BUCKET)==0,"Water delivery fabricated or lost the single bucket");
        for(int z=2;z<=4;z++){
            BlockPos water=anchor.add(3,0,z);
            c.assertTrue(c.getWorld().getBlockState(water).isOf(Blocks.WATER) && c.getWorld().getFluidState(water).isStill(),"Irrigation channel is missing a real source");
        }
        c.assertTrue(wheat(c,anchor)==seeds && count(npc,Items.WHEAT_SEEDS)==0,"Farm did not plant exactly the supplied seeds");
        for(int x:new int[]{8,11,14})c.assertFalse(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,1,12))).isOf(Blocks.WATER),
            "A destination source was created without draining its river source");
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="farm_reusable_bucket",tickLimit=1500)
    public void savedFarmReusesOneBucketForThreeActualWaterTrips(TestContext c){
        CompanionEntity npc=worker(c);river(c);BlockPos origin=npc.getBlockPos(),anchor=origin.add(2,-1,0);
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)if(x==0||x==6||z==0||z==6){
            BlockPos p=anchor.add(x,0,z);c.getWorld().setBlockState(p,Blocks.OAK_LOG.getDefaultState());OwnedBlocks.get(c.getWorld()).claim(p,npc.getUuid());
        }
        npc.backpack.setStack(0,new ItemStack(Items.BUCKET));npc.backpack.setStack(1,new ItemStack(Items.IRON_HOE));
        npc.backpack.setStack(2,new ItemStack(Items.WHEAT_SEEDS,3));
        npc.tasks().startAt(CompanionTasks.Kind.CREATE_FARM,origin,null);
        NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
        saved.getCompound("FarmProject").putString("Phase","WATER");saved.getCompound("FarmProject").putInt("Cursor",0);
        npc.tasks().readNbt(saved);
        c.runAtTick(1300,()->{finished(c,npc,anchor,3);c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="farm_complete_construction",tickLimit=2800)
    public void buildsIrrigatedFarmThroughSnowGrassAndNaturalBump(TestContext c){
        CompanionEntity npc=worker(c);river(c);BlockPos origin=npc.getBlockPos(),anchor=origin.add(2,-1,0);
        c.getWorld().setBlockState(anchor.add(1,1,1),Blocks.SNOW.getDefaultState());
        c.getWorld().setBlockState(anchor.add(2,1,1),Blocks.GRASS.getDefaultState());
        c.getWorld().setBlockState(anchor.add(4,1,3),Blocks.DIRT.getDefaultState());
        npc.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,24));npc.backpack.setStack(1,new ItemStack(Items.DIRT,8));
        npc.backpack.setStack(2,new ItemStack(Items.BUCKET));npc.backpack.setStack(3,new ItemStack(Items.IRON_HOE));
        npc.backpack.setStack(4,new ItemStack(Items.WHEAT_SEEDS,4));
        npc.tasks().startAt(CompanionTasks.Kind.CREATE_FARM,origin,null);
        c.runAtTick(2500,()->{
            finished(c,npc,anchor,4);
            for(int x=0;x<7;x++)for(int z=0;z<7;z++)if(x==0||x==6||z==0||z==6)
                c.assertTrue(c.getWorld().getBlockState(anchor.add(x,0,z)).isOf(Blocks.SPRUCE_LOG),"Farm mixed or fabricated its wood border");
            c.assertTrue(count(npc,Items.SPRUCE_LOG)==0,"Farm border did not consume exactly 24 logs");c.complete();
        });
    }
    private static final class BlockStateCheck {
        static void replanted(TestContext c,BlockPos pos){
            var crop=c.getWorld().getBlockState(pos);
            c.assertTrue(crop.isOf(Blocks.WHEAT) && !((CropBlock)crop.getBlock()).isMature(crop),"Existing field was not replanted after collecting its drops");
        }
    }
}
