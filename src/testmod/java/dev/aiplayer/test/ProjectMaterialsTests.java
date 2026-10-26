package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.ProjectMaterials;
import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.FurnaceBlockEntity;
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
import java.util.HashSet;
import java.util.Set;

/** Dependency tests use actual inventories, block entities and vanilla furnace
 * ticks. The worker is unowned until a test explicitly enables physical pickup,
 * so an unrelated autonomous recipe cannot consume the fixture's ingredients. */
public final class ProjectMaterialsTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(3,2,4));
        npc.autoCraft=true;npc.autoEquip=true;npc.autoLoot=true;npc.autoSurvive=false;npc.setMode(Mode.WAIT);
        return npc;
    }
    private ProjectMaterials materials(CompanionEntity npc){return new ProjectMaterials(npc,new ProjectWork(npc));}
    private void bench(TestContext c){c.setBlockState(new BlockPos(3,2,6),Blocks.CRAFTING_TABLE);}
    private BlockPos furnace(TestContext c,CompanionEntity npc,ProjectMaterials materials){
        BlockPos pos=c.getAbsolutePos(new BlockPos(5,2,4));
        c.getWorld().setBlockState(pos,Blocks.FURNACE.getDefaultState());
        OwnedBlocks.get(c.getWorld()).claim(pos,npc.getUuid());materials.preferFurnace(pos);return pos;
    }
    private void owner(CompanionEntity npc){
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=320)
    public void emptyProjectGetsItsFirstRealLogThroughGrassWithoutAFreeTool(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectMaterials materials=materials(npc);
        for(int x=0;x<12;x++)for(int z=0;z<10;z++){
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
        }
        BlockPos base=c.getAbsolutePos(new BlockPos(7,2,4));
        c.getWorld().setBlockState(base,Blocks.OAK_LOG.getDefaultState());
        c.getWorld().setBlockState(base.up(),Blocks.OAK_LOG.getDefaultState());
        // Both trunk levels are surrounded by noncolliding tall grass, with
        // dirt below and leaves above. Neither log has a literal AIR neighbor.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            c.getWorld().setBlockState(base.add(x,2,z),Blocks.OAK_LEAVES.getDefaultState());
        }
        for(var side:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            c.getWorld().setBlockState(base.offset(side),Blocks.TALL_GRASS.getDefaultState());
            c.getWorld().setBlockState(base.offset(side).up(),Blocks.TALL_GRASS.getDefaultState()
                .with(net.minecraft.block.TallPlantBlock.HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER));
        }
        boolean[] physicalDrop={false};
        c.assertTrue(npc.backpack.isEmpty() && npc.getMainHandStack().isEmpty(),"Bootstrap fixture contains supplied materials");
        c.runAtEveryTick(()->{
            materials.ensure(Items.OAK_LOG,1);
            if(!c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(base).expand(3),
                    e->e.isAlive() && e.getStack().isOf(Items.OAK_LOG)
                        && e.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).isEmpty())physicalDrop[0]=true;
        });
        c.runAtTick(260,()->{
            c.assertTrue(c.getWorld().getBlockState(base).isAir() && physicalDrop[0],
                "Empty project did not physically break its grass-surrounded first log: "+materials.status());
            c.assertTrue(materials.count(Items.OAK_LOG)==1,"First physical log was not picked up: "+materials.status());
            c.assertTrue(materials.count(Items.WOODEN_AXE)==0 && materials.count(Items.CRAFTING_TABLE)==0,
                "One-log bootstrap invented a tool or consumed its requested log for an unnecessary recipe");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=220)
    public void missingWorkbenchPhysicallyClearsDenseGrassBeforePlacement(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);
        for(int x=0;x<9;x++)for(int z=0;z<10;z++){
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
        }
        BlockPos preferred=c.getAbsolutePos(new BlockPos(3,2,6));materials.preferWorkbench(preferred);
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,3));
        npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,8));
        c.assertTrue(c.getWorld().getBlockState(preferred).isOf(Blocks.GRASS),"Station fixture has no vegetation to clear");
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(170,()->{
            c.assertTrue(materials.count(Items.BUCKET)==1,"Grass stalled the station/recipe dependency: "+materials.status());
            c.assertTrue(c.getWorld().getBlockState(preferred).isOf(Blocks.CRAFTING_TABLE)
                    && OwnedBlocks.get(c.getWorld()).owned(preferred,npc.getUuid()),
                "No real owned workbench replaced the station vegetation");
            c.assertTrue(materials.count(Items.OAK_PLANKS)==4 && materials.count(Items.IRON_INGOT)==0
                    && materials.count(Items.CRAFTING_TABLE)==0,"Grass preparation changed the real recipe costs");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=1800)
    public void oneLogAtATimeReservesAndRestoresItsAxeThenRenewsAWornOne(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectWork work=new ProjectWork(npc);ProjectMaterials[] materials={new ProjectMaterials(npc,work)};
        BlockPos bench=c.getAbsolutePos(new BlockPos(3,2,6));materials[0].preferWorkbench(bench);
        for(int x=0;x<26;x++)for(int z=0;z<14;z++){
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
        }
        for(int x:new int[]{8,12,16,20}){
            for(int y=2;y<=4;y++)c.setBlockState(new BlockPos(x,y,4),Blocks.OAK_LOG);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.setBlockState(new BlockPos(x+dx,5,4+dz),Blocks.OAK_LEAVES);
        }
        int[] placed={0};boolean[] resumed={false},wore={false},renewed={false};
        c.runAtEveryTick(()->{
            if(placed[0]>=4)return;
            boolean available=materials[0].ensure(Items.OAK_LOG,1);
            NbtCompound state=new NbtCompound();materials[0].writeNbt(state);
            if(!resumed[0] && state.getCompound("ProjectMaterials").getBoolean("AxePending")){
                materials[0]=new ProjectMaterials(npc,work);materials[0].readNbt(state);resumed[0]=true;
            }
            ItemStack axe=npc.getMainHandStack().isOf(Items.WOODEN_AXE)?npc.getMainHandStack():ItemStack.EMPTY;
            for(int i=0;i<npc.backpack.size() && axe.isEmpty();i++)
                if(npc.backpack.getStack(i).isOf(Items.WOODEN_AXE))axe=npc.backpack.getStack(i);
            if(!wore[0] && !axe.isEmpty()){
                axe.setDamage(axe.getMaxDamage()-1);wore[0]=true;
            }else if(wore[0] && !axe.isEmpty() && !dev.aiplayer.ai.brain.ResourcePlan.worn(axe))renewed[0]=true;
            if(available){
                if(placed[0]>0)c.assertTrue(renewed[0],"Parent consumed the axe subgoal's next log before a real replacement existed");
                BlockPos next=c.getAbsolutePos(new BlockPos(4+placed[0],2,10));
                if(!c.getWorld().getBlockState(next).isAir()){work.clearNatural(next);return;}
                if(work.place(next,Blocks.OAK_LOG.getDefaultState(),Items.OAK_LOG,1))placed[0]++;
            }
        });
        c.runAtTick(1660,()->{
            c.assertTrue(placed[0]==4,"Repeated one-log parent stalled: placed="+placed[0]+" "+materials[0].status());
            c.assertTrue(resumed[0] && wore[0] && renewed[0],"Axe dependency did not persist through save and worn-tool renewal");
            c.assertTrue(materials[0].count(Items.WOODEN_AXE)==1
                    && dev.aiplayer.ai.brain.ResourcePlan.usable(npc,net.minecraft.item.AxeItem.class),
                "Project did not retain exactly one usable real replacement axe");
            c.assertTrue(c.getWorld().getBlockState(bench).isOf(Blocks.CRAFTING_TABLE)
                    && OwnedBlocks.get(c.getWorld()).owned(bench,npc.getUuid()),"Axe bootstrap used a virtual bench");
            for(int i=0;i<4;i++)c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4+i,2,10))).isOf(Blocks.OAK_LOG),
                "Sequential log delivery did not place its real block");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=160)
    public void craftsExactlyOneBucketFromThreeIngotsAtARealBench(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);bench(c);
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,3));
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(110,()->{
            c.assertTrue(materials.count(Items.BUCKET)==1,"Bucket recipe never completed: "+materials.status());
            c.assertTrue(materials.count(Items.IRON_INGOT)==0,"Bucket did not consume exactly three real ingots");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(3,2,6))).isOf(Blocks.CRAFTING_TABLE),
                "Recipe removed its real workbench");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=360)
    public void bucketCraftingBuildsAndPlacesItsMissingWorkbench(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,3));
        npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,8));
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(150,()->{
            c.assertTrue(materials.count(Items.BUCKET)==1 && materials.count(Items.IRON_INGOT)==0,
                "Bucket dependency failed to create/use a real workbench: "+materials.status());
            c.assertTrue(materials.count(Items.OAK_PLANKS)==4 && materials.count(Items.CRAFTING_TABLE)==0,
                "Workbench recipe or placement did not conserve its four planks");
            int benches=0;
            for(BlockPos p:BlockPos.iterate(npc.getBlockPos().add(-4,-1,-4),npc.getBlockPos().add(4,1,4)))
                if(c.getWorld().getBlockState(p).isOf(Blocks.CRAFTING_TABLE)
                    && OwnedBlocks.get(c.getWorld()).owned(p,npc.getUuid()))benches++;
            c.assertTrue(benches==1,"Planner crafted a virtual/duplicate workbench");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=360)
    public void furnaceConsumesRealIronAndFuelAndResumesAfterSave(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials[] materials={materials(npc)};
        BlockPos oven=furnace(c,npc,materials[0]);
        npc.backpack.setStack(0,new ItemStack(Items.RAW_IRON));
        npc.backpack.setStack(1,new ItemStack(Items.COAL));
        c.runAtEveryTick(()->materials[0].ensure(Items.IRON_INGOT,1));
        c.runAtTick(80,()->{
            c.assertTrue(materials[0].count(Items.IRON_INGOT)==0,"Smelting bypassed the vanilla cook time");
            c.assertTrue(c.getWorld().getBlockEntity(oven) instanceof FurnaceBlockEntity,
                "No real furnace block entity exists");
            NbtCompound saved=new NbtCompound();materials[0].writeNbt(saved);
            materials[0]=materials(npc);materials[0].readNbt(saved);
        });
        c.runAtTick(290,()->{
            c.assertTrue(materials[0].count(Items.IRON_INGOT)==1,"Vanilla iron smelting did not finish: "+materials[0].status());
            c.assertTrue(materials[0].count(Items.RAW_IRON)==0 && materials[0].count(Items.COAL)==0,
                "Smelting duplicated raw iron or fuel");
            FurnaceBlockEntity block=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oven);
            c.assertTrue(block!=null && block.getStack(0).isEmpty() && block.getStack(2).isEmpty(),
                "Smelted input/output was duplicated between furnace and inventory");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=520)
    public void cancelledProjectUsesNewLocalStationsInsteadOfItsOldFarm(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);
        BlockPos oldBench=c.getAbsolutePos(new BlockPos(25,2,27));
        BlockPos oldFurnace=c.getAbsolutePos(new BlockPos(25,2,25));
        c.getWorld().setBlockState(oldBench,Blocks.CRAFTING_TABLE.getDefaultState());
        c.getWorld().setBlockState(oldFurnace,Blocks.FURNACE.getDefaultState());
        OwnedBlocks.get(c.getWorld()).claim(oldBench,npc.getUuid());
        OwnedBlocks.get(c.getWorld()).claim(oldFurnace,npc.getUuid());
        NbtCompound previous=new NbtCompound(),saved=new NbtCompound();
        previous.putString("Dimension",c.getWorld().getRegistryKey().getValue().toString());
        previous.putString("Log","minecraft:oak_log");previous.putString("Planks","minecraft:oak_planks");
        previous.putLong("Bench",oldBench.asLong());previous.putLong("Furnace",oldFurnace.asLong());
        previous.putLong("PreferredBench",oldBench.asLong());previous.putLong("PreferredFurnace",oldFurnace.asLong());
        saved.put("ProjectMaterials",previous);materials.readNbt(saved);
        materials.pause();NbtCompound paused=new NbtCompound();materials.writeNbt(paused);
        c.assertTrue(paused.getCompound("ProjectMaterials").getLong("Bench")==oldBench.asLong()
            && paused.getCompound("ProjectMaterials").getLong("Furnace")==oldFurnace.asLong(),
            "Pausing a saved project discarded its existing stations");

        materials.cancel();
        BlockPos nextBench=c.getAbsolutePos(new BlockPos(3,2,6));
        BlockPos nextFurnace=c.getAbsolutePos(new BlockPos(5,2,4));
        materials.preferWorkbench(nextBench);materials.preferFurnace(nextFurnace);
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,2));
        npc.backpack.setStack(1,new ItemStack(Items.RAW_IRON));
        npc.backpack.setStack(2,new ItemStack(Items.COAL));
        npc.backpack.setStack(3,new ItemStack(Items.OAK_PLANKS,4));
        npc.backpack.setStack(4,new ItemStack(Items.COBBLESTONE,8));
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(420,()->{
            c.assertTrue(materials.count(Items.BUCKET)==1,"New project stalled at previous stations: "+materials.status());
            c.assertTrue(c.getWorld().getBlockState(nextBench).isOf(Blocks.CRAFTING_TABLE)
                && c.getWorld().getBlockEntity(nextFurnace) instanceof FurnaceBlockEntity,
                "New project reused a distant station instead of placing its local stations");
            c.assertTrue(OwnedBlocks.get(c.getWorld()).owned(nextBench,npc.getUuid())
                && OwnedBlocks.get(c.getWorld()).owned(nextFurnace,npc.getUuid()),
                "New local stations were not real owned project blocks");
            c.assertTrue(materials.count(Items.IRON_INGOT)==0 && materials.count(Items.RAW_IRON)==0
                && materials.count(Items.COAL)==0 && materials.count(Items.OAK_PLANKS)==0
                && materials.count(Items.COBBLESTONE)==0,"Restarted project did not conserve its real recipe inputs");
            FurnaceBlockEntity untouched=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oldFurnace);
            c.assertTrue(c.getWorld().getBlockState(oldBench).isOf(Blocks.CRAFTING_TABLE)
                && untouched!=null && untouched.getStack(0).isEmpty() && untouched.getStack(1).isEmpty()
                && untouched.getStack(2).isEmpty(),"Cancelled project stations were changed by the next project");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=340)
    public void glassComesFromSandCookedInAVanillaFurnace(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);furnace(c,npc,materials);
        npc.backpack.setStack(0,new ItemStack(Items.SAND));npc.backpack.setStack(1,new ItemStack(Items.CHARCOAL));
        c.runAtEveryTick(()->materials.ensure(Items.GLASS,1));
        c.runAtTick(90,()->c.assertTrue(materials.count(Items.GLASS)==0,"Glass appeared before a real furnace cycle"));
        c.runAtTick(280,()->{
            c.assertTrue(materials.count(Items.GLASS)==1,"Sand was not smelted into real glass: "+materials.status());
            c.assertTrue(materials.count(Items.SAND)==0 && materials.count(Items.CHARCOAL)==0,
                "Glass production did not consume its sand and fuel");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=1200)
    public void smeltingLeavesItsFurnaceToMineDistantSandAndReturnsWithRealInput(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectMaterials materials=materials(npc);BlockPos oven=furnace(c,npc,materials);
        BlockPos sand=c.getAbsolutePos(new BlockPos(20,2,4));c.getWorld().setBlockState(sand,Blocks.SAND.getDefaultState());
        npc.backpack.setStack(0,new ItemStack(Items.COAL));
        boolean[] leftOven={false},sawRealDrop={false};
        c.runAtEveryTick(()->{
            if(npc.squaredDistanceTo(Vec3d.ofCenter(oven))>8*8)leftOven[0]=true;
            if(!c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(sand).expand(3),
                    e->e.isAlive() && e.getStack().isOf(Items.SAND)
                        && e.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).isEmpty())sawRealDrop[0]=true;
            FurnaceBlockEntity furnace=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oven);
            int input=furnace.getStack(0).getCount(),fuel=furnace.getStack(1).getCount(),glass=materials.count(Items.GLASS);
            materials.ensure(Items.GLASS,1);
            if(furnace.getStack(0).getCount()>input || furnace.getStack(1).getCount()>fuel || materials.count(Items.GLASS)>glass)
                c.assertTrue(npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(oven))<=3.5*3.5,
                    "Furnace inventory was mutated while the worker was away gathering sand");
        });
        c.runAtTick(1080,()->{
            c.assertTrue(leftOven[0] && sawRealDrop[0] && c.getWorld().getBlockState(sand).isAir(),
                "Smelting kept the worker near its oven instead of physically mining distant sand: "+materials.diagnostics());
            c.assertTrue(materials.count(Items.GLASS)==1 && materials.count(Items.SAND)==0 && materials.count(Items.COAL)==0,
                "Distant sand/furnace chain did not conserve its real input and fuel: "+materials.status());c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=1200)
    public void occupiedUnfuelledFurnaceLetsWorkerFetchDistantWoodBeforeRefuelling(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectMaterials materials=materials(npc);BlockPos oven=furnace(c,npc,materials);
        FurnaceBlockEntity furnace=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oven);
        furnace.setStack(0,new ItemStack(Items.RAW_IRON));furnace.markDirty();
        BlockPos tree=c.getAbsolutePos(new BlockPos(20,2,4));
        for(int y=0;y<3;y++)c.getWorld().setBlockState(tree.up(y),Blocks.OAK_LOG.getDefaultState());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)c.getWorld().setBlockState(tree.add(x,3,z),Blocks.OAK_LEAVES.getDefaultState());
        boolean[] leftOven={false};
        c.runAtEveryTick(()->{
            if(npc.squaredDistanceTo(Vec3d.ofCenter(oven))>8*8)leftOven[0]=true;
            int fuel=furnace.getStack(1).getCount(),iron=materials.count(Items.IRON_INGOT);
            materials.ensure(Items.IRON_INGOT,1);
            if(furnace.getStack(1).getCount()>fuel || materials.count(Items.IRON_INGOT)>iron)
                c.assertTrue(npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(oven))<=3.5*3.5,
                    "Existing furnace input was fuelled or collected remotely");
        });
        c.runAtTick(1080,()->{
            c.assertTrue(leftOven[0] && c.getWorld().getBlockState(tree).isAir(),
                "Existing furnace input blocked the trip for real wood fuel: "+materials.diagnostics());
            c.assertTrue(materials.count(Items.IRON_INGOT)==1 && materials.count(Items.OAK_PLANKS)==3
                    && materials.count(Items.OAK_LOG)==0 && furnace.getStack(0).isEmpty() && furnace.getStack(2).isEmpty(),
                "Wood fuel/iron chain did not consume exactly one real plank from its gathered log: "+materials.status());c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=900)
    public void missingCoalUsesCharcoalFromARealLogAndPlankFuel(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);furnace(c,npc,materials);
        npc.backpack.setStack(0,new ItemStack(Items.RAW_IRON));
        npc.backpack.setStack(1,new ItemStack(Items.OAK_LOG,2));
        npc.backpack.setStack(2,new ItemStack(Items.OAK_PLANKS));
        c.runAtEveryTick(()->materials.ensure(Items.IRON_INGOT,1));
        c.runAtTick(160,()->c.assertTrue(materials.count(Items.IRON_INGOT)==0,
            "Charcoal prerequisite bypassed the two real smelting cycles"));
        c.runAtTick(550,()->{
            c.assertTrue(materials.count(Items.IRON_INGOT)==1,"Charcoal/iron prerequisite chain did not complete: "+materials.status());
            c.assertTrue(materials.count(Items.RAW_IRON)==0 && materials.count(Items.OAK_LOG)==1
                && materials.count(Items.OAK_PLANKS)==0 && materials.count(Items.CHARCOAL)==0
                && materials.count(Items.COAL)==0,"Charcoal smelting fabricated or lost resources");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=900)
    public void smeltsThreeIronInputsThenMakesOneReusableBucket(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);bench(c);furnace(c,npc,materials);
        npc.backpack.setStack(0,new ItemStack(Items.RAW_IRON,3));npc.backpack.setStack(1,new ItemStack(Items.COAL));
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(300,()->c.assertTrue(materials.count(Items.BUCKET)==0,"Bucket appeared before all three ingots were smelted"));
        c.runAtTick(790,()->{
            c.assertTrue(materials.count(Items.BUCKET)==1,"Iron/furnace/bucket dependency chain did not finish: "+materials.status());
            c.assertTrue(materials.count(Items.RAW_IRON)==0 && materials.count(Items.IRON_INGOT)==0
                && materials.count(Items.COAL)==0,"Bucket chain fabricated or retained consumed inputs");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=1900)
    public void bucketGathersThreeDistantOresBeforeLoadingOneRealFuelBatch(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectMaterials materials=materials(npc);BlockPos oven=furnace(c,npc,materials);
        // The ore route returns from the east; keep the supplied real bench
        // beside that oven work area so this test isolates ore batching.
        c.setBlockState(new BlockPos(8,2,6),Blocks.CRAFTING_TABLE);
        BlockPos[] ores={c.getAbsolutePos(new BlockPos(20,2,4)),c.getAbsolutePos(new BlockPos(20,2,7)),c.getAbsolutePos(new BlockPos(20,2,10))};
        for(BlockPos ore:ores)c.getWorld().setBlockState(ore,Blocks.IRON_ORE.getDefaultState());
        npc.backpack.setStack(0,new ItemStack(Items.STONE_PICKAXE));npc.backpack.setStack(1,new ItemStack(Items.COAL));
        Set<UUID> drops=new HashSet<>();int[] firstLoad={-1},loads={0};boolean[] leftOven={false},madeBucket={false};
        c.runAtEveryTick(()->{
            if(npc.squaredDistanceTo(Vec3d.ofCenter(oven))>8*8)leftOven[0]=true;
            FurnaceBlockEntity furnace=(FurnaceBlockEntity)c.getWorld().getBlockEntity(oven);
            int input=furnace.getStack(0).getCount(),raw=materials.count(Items.RAW_IRON),fuel=furnace.getStack(1).getCount();
            materials.ensure(Items.BUCKET,1);
            for(ItemEntity drop:c.getWorld().getEntitiesByClass(ItemEntity.class,npc.getBoundingBox().expand(32),
                    e->e.isAlive() && e.getStack().isOf(Items.RAW_IRON)
                        && e.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())))drops.add(drop.getUuid());
            if(furnace.getStack(0).getCount()>input || furnace.getStack(1).getCount()>fuel){
                c.assertTrue(npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(oven))<=3.5*3.5,"Ore batch was inserted remotely");
                for(BlockPos ore:ores)c.assertTrue(c.getWorld().getBlockState(ore).isAir(),"Furnace was fuelled before the required ore expedition finished");
            }
            if(furnace.getStack(0).getCount()>input){
                c.assertTrue(input==0 && raw==3 && furnace.getStack(0).getCount()==3,
                    "Bucket lost its three-ore goal and repeatedly loaded one input at a time");
                firstLoad[0]=npc.age;loads[0]++;
            }
            if(materials.count(Items.BUCKET)>0 && !madeBucket[0]){
                c.assertTrue(firstLoad[0]>=0 && npc.age-firstLoad[0]>=600,"Three iron ingots bypassed their vanilla furnace cook time");
                madeBucket[0]=true;
            }
        });
        c.runAtTick(1770,()->{
            c.assertTrue(leftOven[0] && drops.size()==3 && loads[0]==1,"Ore batching skipped physical mining, pickup or a single real furnace load: "+materials.diagnostics());
            c.assertTrue(madeBucket[0] && materials.count(Items.BUCKET)==1 && materials.count(Items.RAW_IRON)==0
                    && materials.count(Items.IRON_INGOT)==0 && materials.count(Items.COAL)==0,
                "Three distant ores and one fuel did not become exactly one real bucket: "+materials.diagnostics());c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=150)
    public void disabledCraftingPreservesIngredientsAndCreatesNoBucket(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);bench(c);npc.autoCraft=false;
        npc.backpack.setStack(0,new ItemStack(Items.IRON_INGOT,3));
        c.runAtEveryTick(()->materials.ensure(Items.BUCKET,1));
        c.runAtTick(90,()->{
            c.assertTrue(materials.count(Items.IRON_INGOT)==3 && materials.count(Items.BUCKET)==0,
                "Disabled crafting consumed ingredients or created a bucket");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=150)
    public void selectedWoodRecipeDoesNotConvertAnotherSpecies(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);
        materials.selectWood(Items.SPRUCE_LOG,Items.SPRUCE_PLANKS);
        npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG,10));npc.backpack.setStack(1,new ItemStack(Items.SPRUCE_LOG));
        c.runAtEveryTick(()->materials.ensure(Items.SPRUCE_PLANKS,4));
        c.runAtTick(90,()->{
            c.assertTrue(materials.count(Items.SPRUCE_PLANKS)==4 && materials.count(Items.SPRUCE_LOG)==0,
                "Selected-species planks recipe was not conserved");
            c.assertTrue(materials.count(Items.OAK_LOG)==10 && materials.count(Items.OAK_PLANKS)==0,
                "Material planner substituted another wood species");
            c.assertFalse(materials.ensure(Items.DIAMOND,1),"Unsupported material was granted by the planner");
            c.assertTrue(materials.count(Items.DIAMOND)==0,"A missing material appeared in inventory");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=170)
    public void ironMiningCraftsSuitablePickThenSpawnsLootBeforePickup(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);bench(c);
        BlockPos ore=c.getAbsolutePos(new BlockPos(4,2,4));c.getWorld().setBlockState(ore,Blocks.IRON_ORE.getDefaultState());
        npc.backpack.setStack(0,new ItemStack(Items.WOODEN_PICKAXE));
        npc.backpack.setStack(1,new ItemStack(Items.COBBLESTONE,3));npc.backpack.setStack(2,new ItemStack(Items.STICK,2));
        npc.backpack.setStack(3,new ItemStack(Items.DIAMOND_AXE));npc.backpack.setStack(4,new ItemStack(Items.DIAMOND_SWORD));
        c.runAtEveryTick(()->materials.ensure(Items.RAW_IRON,1));
        c.runAtTick(5,()->c.assertTrue(c.getWorld().getBlockState(ore).isOf(Blocks.IRON_ORE),
            "Iron was destroyed before a suitable pickaxe was crafted"));
        c.runAtTick(60,()->{
            c.assertTrue(c.getWorld().getBlockState(ore).isAir(),"Suitable pick did not physically mine iron: "+materials.status());
            c.assertTrue(materials.count(Items.RAW_IRON)==0,"Iron was inserted remotely instead of spawning loot");
            c.assertTrue(!c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(ore).expand(3),
                item->item.isAlive() && item.getStack().isOf(Items.RAW_IRON)
                    && item.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).isEmpty(),
                "Physical iron mining spawned no tagged real drop");
            owner(npc);npc.setMode(Mode.GUARD);
        });
        c.runAtTick(130,()->{
            c.assertTrue(materials.count(Items.RAW_IRON)==1,"Real iron drop was not physically picked up: "+materials.status());
            c.assertTrue(materials.count(Items.COBBLESTONE)==0 && materials.count(Items.STICK)==0,
                "Stone pickaxe did not consume its three cobblestones and two sticks");
            c.assertTrue(materials.count(Items.STONE_PICKAXE)==1,"Required stone pickaxe was not retained");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=220)
    public void wornPickDoesNotSatisfyTheMiningPrerequisite(TestContext c){
        CompanionEntity npc=worker(c);ProjectMaterials materials=materials(npc);bench(c);
        BlockPos stone=c.getAbsolutePos(new BlockPos(4,2,4));
        c.getWorld().setBlockState(stone,Blocks.STONE.getDefaultState());
        ItemStack worn=new ItemStack(Items.WOODEN_PICKAXE);worn.setDamage(worn.getMaxDamage()-1);
        npc.backpack.setStack(0,worn);
        npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,3));
        npc.backpack.setStack(2,new ItemStack(Items.STICK,2));
        c.runAtEveryTick(()->materials.ensure(Items.COBBLESTONE,1));
        c.runAtTick(70,()->{
            c.assertTrue(c.getWorld().getBlockState(stone).isAir(),
                "Existing worn pick starved its actual replacement: "+materials.status()+" "+materials.diagnostics());
            c.assertTrue(materials.count(Items.COBBLESTONE)==0 && !c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(stone).expand(3),
                    e->e.isAlive() && e.getStack().isOf(Items.COBBLESTONE)
                        && e.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).isEmpty(),
                "Replacement pick did not produce a real stone drop before physical pickup");
            owner(npc);
        });
        c.runAtTick(160,()->{
            c.assertTrue(materials.count(Items.COBBLESTONE)==1,"Replacement pick's real drop was not picked up");
            c.assertTrue(materials.count(Items.WOODEN_PICKAXE)==2
                    && dev.aiplayer.ai.brain.ResourcePlan.usable(npc,net.minecraft.item.PickaxeItem.class),
                "No additional usable pick was crafted while preserving the old worn stack");
            c.assertTrue(materials.count(Items.OAK_PLANKS)==0 && materials.count(Items.STICK)==0,
                "Replacement pick did not consume exactly three planks and two sticks");
            c.assertTrue(worn.getDamage()==worn.getMaxDamage()-1,"Project mined with its worn pick");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_materials",tickLimit=1400)
    public void nestedOreToolDependencyCanAbandonAnUnreachableWoodDrop(TestContext c){
        CompanionEntity npc=worker(c);owner(npc);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,npc.getBlockPos(),null);
        ProjectMaterials materials=materials(npc);bench(c);
        npc.backpack.setStack(0,new ItemStack(Items.COBBLESTONE,3));
        BlockPos ore=c.getAbsolutePos(new BlockPos(4,2,4));c.getWorld().setBlockState(ore,Blocks.IRON_ORE.getDefaultState());
        BlockPos tree=c.getAbsolutePos(new BlockPos(7,2,4));
        for(int y=0;y<3;y++)c.getWorld().setBlockState(tree.up(y),Blocks.OAK_LOG.getDefaultState());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)c.getWorld().setBlockState(tree.add(x,3,z),Blocks.OAK_LEAVES.getDefaultState());
        BlockPos sealed=c.getAbsolutePos(new BlockPos(12,3,12));
        for(BlockPos pos:BlockPos.iterate(sealed.add(-1,-1,-1),sealed.add(1,1,1)))
            c.getWorld().setBlockState(pos,pos.equals(sealed)?Blocks.AIR.getDefaultState():Blocks.STONE.getDefaultState());
        ItemEntity unreachable=new ItemEntity(c.getWorld(),sealed.getX()+0.5,sealed.getY()+0.25,sealed.getZ()+0.5,new ItemStack(Items.OAK_LOG));
        unreachable.setPickupDelay(0);unreachable.addCommandTag("aiplayer_drop_"+npc.getUuid());c.getWorld().spawnEntity(unreachable);
        c.runAtEveryTick(()->materials.ensure(Items.RAW_IRON,1));
        c.runAtTick(1280,()->{
            c.assertTrue(materials.count(Items.RAW_IRON)==1 && c.getWorld().getBlockState(ore).isAir(),
                "Parent ore search kept resetting its nested wood-drop timeout: "+materials.diagnostics());
            c.assertTrue(unreachable.isAlive() && unreachable.getStack().isOf(Items.OAK_LOG),
                "Nested dependency waited for the inaccessible drop to despawn or collected it remotely");
            c.assertTrue(c.getWorld().getBlockState(tree).isAir() && materials.count(Items.STONE_PICKAXE)==1
                    && materials.count(Items.COBBLESTONE)==0,"Pickaxe dependency skipped its alternate real timber or recipe costs");c.complete();
        });
    }
}
