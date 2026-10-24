package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** End-to-end server NPC process tests. Inventory fixtures provide materials;
 * production code must consume them and move the entity physically. */
public final class CompanionTaskTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(15,2,15));
        NbtCompound owner=new NbtCompound();npc.writeCustomDataToNbt(owner);
        owner.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(owner);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=true;
        return npc;
    }
    private int plankCount(CompanionEntity npc){return npc.backpack.count(Items.OAK_PLANKS);}
    private int placed(TestContext c,CompanionEntity npc){
        int count=0;
        for(int x=17;x<=21;x++)for(int y=2;y<=5;y++)for(int z=15;z<=19;z++){
            BlockPos pos=c.getAbsolutePos(new BlockPos(x,y,z));
            if(c.getWorld().getBlockState(pos).isOf(Blocks.OAK_PLANKS)
                    && OwnedBlocks.get(c.getWorld()).owned(pos,npc.getUuid()))count++;
        }
        return count;
    }
    private BlockPos farmSoil(TestContext c){
        BlockPos soil=new BlockPos(18,1,15);
        c.setBlockState(soil,Blocks.DIRT);c.setBlockState(new BlockPos(18,1,17),Blocks.WATER);
        return soil;
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=1200)
    public void buildsShelterFromExactlyEightyRealPlanks(TestContext c){
        CompanionEntity npc=worker(c);
        npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,64));
        npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,16));
        npc.tasks().start(CompanionTasks.Kind.BUILD_SHELTER);
        c.runAtTick(1100,()->{
            c.assertTrue(!npc.tasks().active(),"Shelter did not complete: "+npc.tasks().status()+" pos="+npc.getPos());
            c.assertTrue(placed(c,npc)==80 && plankCount(npc)==0,
                "Shelter duplicated/lost materials: placed="+placed(c,npc)+" inventory="+plankCount(npc));
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(19,3,15))).isAir()
                && c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(19,4,15))).isAir(),"Shelter has no two-high doorway");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=140)
    public void shelterPreservesProtectedObstructionAndMaterials(TestContext c){
        CompanionEntity npc=worker(c);npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,10));
        BlockPos obstacle=c.getAbsolutePos(new BlockPos(17,2,15));
        c.getWorld().setBlockState(obstacle,Blocks.BRICKS.getDefaultState());
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),obstacle);
        npc.tasks().start(CompanionTasks.Kind.BUILD_SHELTER);
        c.runAtTick(100,()->{
            c.assertTrue(c.getWorld().getBlockState(obstacle).isOf(Blocks.BRICKS),"Builder erased player construction");
            c.assertTrue(plankCount(npc)==10 && placed(c,npc)==0,"Blocked shelter consumed materials");
            c.assertTrue(npc.tasks().active(),"Blocked project silently claimed completion");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=230)
    public void shelterConvertsOneRealLogIntoFourPlacedPlanks(TestContext c){
        CompanionEntity npc=worker(c);npc.autoCraft=true;
        npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG));
        npc.backpack.setStack(1,new ItemStack(Items.WOODEN_AXE));
        npc.backpack.setStack(2,new ItemStack(Items.WOODEN_PICKAXE));
        npc.backpack.setStack(3,new ItemStack(Items.WOODEN_SWORD));
        npc.backpack.setStack(4,new ItemStack(Items.CRAFTING_TABLE));
        npc.backpack.setStack(5,new ItemStack(Items.STICK,4));
        npc.tasks().start(CompanionTasks.Kind.BUILD_SHELTER);
        c.runAtTick(180,()->{
            c.assertTrue(npc.backpack.count(Items.OAK_LOG)==0,"Log conversion did not consume its input");
            c.assertTrue(placed(c,npc)+plankCount(npc)==4,"Log conversion fabricated or destroyed wood: "+npc.tasks().status());
            c.assertTrue(placed(c,npc)>0,"Builder never used the converted planks");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=200)
    public void openInventoryPausesProjectAndCancellationStopsIt(TestContext c){
        CompanionEntity npc=worker(c);npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,20));
        npc.tasks().start(CompanionTasks.Kind.BUILD_SHELTER);npc.opened();
        c.runAtTick(55,()->{
            c.assertTrue(plankCount(npc)==20 && placed(c,npc)==0,"Project mutated world while inventory was open");
            npc.closed();
        });
        c.runAtTick(130,()->{
            c.assertTrue(placed(c,npc)>0,"Project did not resume after closing inventory: "+npc.tasks().status());
            npc.tasks().cancel();
        });
        c.runAtTick(170,()->{
            c.assertFalse(npc.tasks().active(),"Cancelled project became active again");
            c.assertTrue(npc.getNavigation().isIdle(),"Cancelled project retained a movement route");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=250)
    public void farmerConsumesSeedAndHoeWithoutHarvestingYoungCrop(TestContext c){
        CompanionEntity npc=worker(c);BlockPos soil=farmSoil(c),crop=soil.up();
        ItemStack hoe=new ItemStack(Items.IRON_HOE);npc.backpack.setStack(0,hoe);
        npc.backpack.setStack(1,new ItemStack(Items.WHEAT_SEEDS,2));
        npc.tasks().start(CompanionTasks.Kind.FARM);
        c.runAtTick(200,()->{
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(soil)).isOf(Blocks.FARMLAND),"Farmer did not till real soil: "+npc.tasks().status());
            var plant=c.getWorld().getBlockState(c.getAbsolutePos(crop));
            c.assertTrue(plant.isOf(Blocks.WHEAT) && !((CropBlock)Blocks.WHEAT).isMature(plant),"Farmer removed a young crop");
            c.assertTrue(npc.backpack.count(Items.WHEAT_SEEDS)==1 && hoe.getDamage()==1,
                "Planting failed to consume exactly one seed and one hoe durability");
            c.assertTrue(npc.backpack.count(Items.WHEAT)==0,"Farmer invented mature wheat");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=500)
    public void farmerPhysicallyCollectsMatureHarvestBeforeReplanting(TestContext c){
        CompanionEntity npc=worker(c);BlockPos soil=farmSoil(c),crop=c.getAbsolutePos(soil.up());
        npc.backpack.setStack(0,new ItemStack(Items.IRON_HOE));
        npc.backpack.setStack(1,new ItemStack(Items.WHEAT_SEEDS,2));
        Vec3d initial=npc.getPos();
        npc.tasks().start(CompanionTasks.Kind.FARM);
        c.runAtTick(90,()->{
            c.assertTrue(c.getWorld().getBlockState(crop).isOf(Blocks.WHEAT),"Fixture crop was never planted: "+npc.tasks().status());
            c.getWorld().setBlockState(crop,((CropBlock)Blocks.WHEAT).withAge(7));
        });
        c.runAtTick(420,()->{
            c.assertTrue(npc.backpack.count(Items.WHEAT)==1,"Mature harvest was not physically collected: "+npc.tasks().status()+" pos="+npc.getPos());
            c.assertTrue(npc.getPos().squaredDistanceTo(initial)>0.5,"Farmer inserted distant drops without approaching");
            var plant=c.getWorld().getBlockState(crop);
            c.assertTrue(plant.isOf(Blocks.WHEAT) && !((CropBlock)Blocks.WHEAT).isMature(plant),"Farmer did not replant after real harvest");
            int wheatDrops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(crop).expand(4),
                e->e.isAlive() && e.getStack().isOf(Items.WHEAT)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(wheatDrops==0,"Harvest was duplicated or left uncollected");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=120)
    public void farmerWaitsForRealHoeAndNeverConsumesSeedsEarly(TestContext c){
        CompanionEntity npc=worker(c);BlockPos soil=farmSoil(c);
        npc.backpack.setStack(0,new ItemStack(Items.WHEAT_SEEDS));
        npc.tasks().start(CompanionTasks.Kind.FARM);
        c.runAtTick(85,()->{
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(soil)).isOf(Blocks.DIRT),"Soil tilled without a hoe");
            c.assertTrue(npc.backpack.count(Items.WHEAT_SEEDS)==1,"Seed consumed before preparing real soil");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(soil.up())).isAir(),"Crop appeared without a valid action");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=1200)
    public void explorationVisitsLoadedLocationsUsingPhysicalMovement(TestContext c){
        CompanionEntity npc=worker(c);Vec3d initial=npc.getPos();
        npc.tasks().start(CompanionTasks.Kind.EXPLORE);
        c.runAtTick(160,()->{
            c.assertTrue(npc.getPos().squaredDistanceTo(initial)>4,"Explorer never walked away from the starting point");
        });
        c.runAtTick(1100,()->{
            c.assertFalse(npc.tasks().active(),"Loaded exploration did not finish: "+npc.tasks().status()+" pos="+npc.getPos());
            c.assertTrue(npc.tasks().progress()==8,"Explorer skipped reachable locations: "+npc.tasks().status());
            c.assertTrue(npc.intelligence().memory().nearest("explored",npc.getBlockPos(),c.getWorld().getTime(),3).isPresent(),
                "Explorer recorded no actual visited location");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="projects",tickLimit=140)
    public void projectProgressPersistsButCannotResumeInAnotherDimension(TestContext c){
        CompanionEntity npc=worker(c);npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,20));
        npc.tasks().start(CompanionTasks.Kind.BUILD_SHELTER);
        c.runAtTick(90,()->{
            NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
            int progress=npc.tasks().progress();c.assertTrue(progress>0,"Project made no progress before save");
            npc.tasks().cancel();CompanionTasks restored=new CompanionTasks(npc);restored.readNbt(saved);
            c.assertTrue(restored.active() && restored.progress()==progress,"Save lost resumable construction progress");
            saved.getCompound("CompanionProject").putString("Dimension","minecraft:the_nether");
            restored.readNbt(saved);c.assertFalse(restored.active(),"Project reused coordinates from a different dimension");
            c.assertTrue(placed(c,npc)==progress,"Save/cancel destroyed completed project blocks");c.complete();
        });
    }
}
