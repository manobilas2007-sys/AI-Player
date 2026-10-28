package dev.aiplayer.test;
import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import java.util.UUID;

public class SurfaceMiningTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        // r5 policy: stone collection starts inside a natural covered chamber.
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,10,z),Blocks.DIRT);
        CompanionEntity a=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(2,2,2));
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        a.autoCraft=false;a.autoSurvive=false;return a;
    }
    private boolean owns(CompanionEntity a,Item item){return a.getMainHandStack().isOf(item)||a.backpack.count(item)>0;}
    private String debug(CompanionEntity a){return " pos="+a.getPos()+" hand="+a.getMainHandStack()+" status="+a.intelligence().status()+" bag="+java.util.stream.IntStream.range(0,a.backpack.size()).mapToObj(i->a.backpack.getStack(i).toString()).toList();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=500)
    public void physicalOrderStopsAfterThreePickups(TestContext c){
        CompanionEntity a=worker(c);a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        for(int z=1;z<=3;z++)c.setBlockState(new BlockPos(4,2,z),Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,3,true);
        c.runAtTick(450,()->{
            c.assertTrue(!a.gathering().active() && a.backpack.count(Items.COBBLESTONE)==3,"Physical collection incomplete"+debug(a));
            c.assertTrue(a.getMainHandStack().getDamage()==3,"Wrong durability for three stone blocks");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=90)
    public void refusesStoneWithoutPick(TestContext c){
        CompanionEntity a=worker(c);c.setBlockState(new BlockPos(4,2,2),Blocks.STONE);a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(70,()->{c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,2,2))).isOf(Blocks.STONE),"Destroyed stone without a pick");c.assertTrue(a.backpack.count(Items.COBBLESTONE)==0 && !a.gathering().active(),"Invalid collection result");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=250)
    public void worldDropBeforeInventory(TestContext c){
        CompanionEntity a=worker(c);a.autoLoot=false;a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos p=c.getAbsolutePos(new BlockPos(4,2,2));c.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());
        var stone=new SurfaceStoneGathering(a);
        for(int t=5;t<170;t++)c.runAtTick(t,stone::tick);
        c.runAtTick(180,()->{
            c.assertTrue(c.getWorld().getBlockState(p).isAir(),"No physical break "+stone.diagnostics(p));
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==0,"Remote insertion");
            int count=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(p).expand(3),e->e.getStack().isOf(Items.COBBLESTONE)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(count==1,"Drop duplicated/lost: "+count);c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface")
    public void rejectsProtectedSupportFluidAndGravity(TestContext c){
        CompanionEntity a=worker(c);var stone=new SurfaceStoneGathering(a);BlockPos p=c.getAbsolutePos(new BlockPos(4,2,2));c.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());
        c.assertTrue(stone.safeBlock(p),"Unobstructed stone rejected");OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),p);
        c.assertFalse(stone.safeBlock(p),"Protected construction accepted");OwnedBlocks.get(c.getWorld()).playerBroke(p);
        c.getWorld().setBlockState(p.east(),Blocks.WATER.getDefaultState());c.assertFalse(stone.safeBlock(p),"Fluid pocket accepted");c.getWorld().setBlockState(p.east(),Blocks.AIR.getDefaultState());
        c.getWorld().setBlockState(p.up(),Blocks.SAND.getDefaultState());c.assertFalse(stone.safeBlock(p),"Falling sand accepted");c.getWorld().setBlockState(p.up(),Blocks.AIR.getDefaultState());
        BlockPos floor=a.getBlockPos().down();c.getWorld().setBlockState(floor,Blocks.STONE.getDefaultState());c.assertFalse(stone.safeBlock(floor),"NPC support accepted");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=450)
    public void usesNonSilkPick(TestContext c){
        CompanionEntity a=worker(c);ItemStack silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.addEnchantment(Enchantments.SILK_TOUCH,1);
        a.equipStack(EquipmentSlot.MAINHAND,silk);a.backpack.setStack(0,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos p=new BlockPos(4,2,2);c.setBlockState(p,Blocks.STONE);a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(400,()->{c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1,"Silk tool selection failed"+debug(a)+new SurfaceStoneGathering(a).diagnostics(c.getAbsolutePos(p)));c.assertTrue(silk.getDamage()==0,"Used silk tool");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=900)
    public void makesPickAndResumesManualOrder(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.WAIT);
        a.backpack.setStack(0,new ItemStack(Items.OAK_LOG,6));a.backpack.setStack(1,new ItemStack(Items.WOODEN_AXE));a.backpack.setStack(2,new ItemStack(Items.WOODEN_SWORD));
        BlockPos p=new BlockPos(4,2,2);c.setBlockState(p,Blocks.STONE);a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(850,()->{c.assertTrue(owns(a,Items.WOODEN_PICKAXE) && a.backpack.count(Items.COBBLESTONE)==1 && !a.gathering().active(),"Prerequisite sequence failed"+debug(a)+new SurfaceStoneGathering(a).diagnostics(c.getAbsolutePos(p)));c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface")
    public void savesAndCancelsMaterialSubgoal(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.WAIT);a.gathering().start(ResourceGathering.Kind.STONE,16,true);a.intelligence().tick();
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);
        c.assertTrue(a.gathering().kind()==ResourceGathering.Kind.WOOD && a.gathering().isManual(),"Lost permission for manual WAIT prerequisite");
        c.assertTrue(n.getCompound("MaterialGatherJob").getCompound("GatherTask").getInt("Desired")==16,"Lost parent quantity");
        CompanionEntity b=AIPlayer.COMPANION.create(c.getWorld());b.readCustomDataFromNbt(n);b.gathering().cancel();NbtCompound out=new NbtCompound();b.writeCustomDataToNbt(out);
        c.assertFalse(out.contains("MaterialGatherJob"),"Cancelled job can resurrect");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=1500)
    public void autonomousThreeStoneTools(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;var owner=c.createMockCreativeServerPlayerInWorld();Vec3d p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(1,2,1)));owner.refreshPositionAndAngles(p.x,p.y,p.z,0,0);
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);n.putUuid("Owner",owner.getUuid());a.readCustomDataFromNbt(n);
        Item[] items={Items.OAK_LOG,Items.OAK_PLANKS,Items.STICK,Items.CRAFTING_TABLE,Items.WOODEN_PICKAXE,Items.WOODEN_AXE,Items.WOODEN_SWORD};int[] counts={4,8,8,1,1,1,1};
        for(int i=0;i<items.length;i++)a.backpack.setStack(i,new ItemStack(items[i],counts[i]));
        for(int z=0;z<8;z++)c.setBlockState(new BlockPos(5,2,z),Blocks.STONE);
        StringBuilder trace=new StringBuilder();
        String[] previous={""};
        c.runAtEveryTick(()->{
            String state=a.gathering().kind()+":"+a.gathering().progress()+"/"+a.gathering().goal()
                +":"+a.gathering().canStartAutomatic()+":"+a.intelligence().activity()+":"+ResourcePlan.stoneNeeded(a)
                +":"+(a.owner()!=null)+":"+SurfaceStoneGathering.caveAccess(a,a.getBlockPos())
                +":"+a.crafting().description();
            if(!state.equals(previous[0])){
                previous[0]=state;
                trace.append("\n age=").append(a.age).append(" ").append(state)
                    .append(" pos=").append(a.getPos()).append(" ownerPos=")
                    .append(a.owner()==null?"missing":a.owner().getPos()).append(" remainingStone=")
                    .append(java.util.stream.IntStream.range(0,8).filter(z->c.getWorld().getBlockState(
                        c.getAbsolutePos(new BlockPos(5,2,z))).isOf(Blocks.STONE)).count())
                    .append(" ").append(a.crafting().diagnostics())
                    .append(" ").append(a.gathering().stoneDiagnostics(c.getAbsolutePos(new BlockPos(5,2,4))));
            }
        });
        c.runAtTick(1400,()->{c.assertTrue(owns(a,Items.STONE_PICKAXE)&&owns(a,Items.STONE_AXE)&&owns(a,Items.STONE_SWORD),"Autonomous upgrade failed"+debug(a)+" "+a.crafting().diagnostics()+trace);c.assertTrue(ResourcePlan.stoneNeeded(a)==0 && a.backpack.count(Items.COBBLESTONE)==0,"Wrong material accounting");owner.discard();c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=1200)
    public void ownedBenchRecoveryYieldsToMissingCobblestone(TestContext c){
        // Regression: the third stone tool must not be starved by a pending
        // reclamation of a REAL crafting table. No item is spawned or granted.
        CompanionEntity a=worker(c);a.autoCraft=true;
        var owner=c.createMockCreativeServerPlayerInWorld();
        Vec3d at=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(1,2,1)));
        owner.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
        NbtCompound ownerNbt=new NbtCompound();a.writeCustomDataToNbt(ownerNbt);
        ownerNbt.putUuid("Owner",owner.getUuid());a.readCustomDataFromNbt(ownerNbt);
        a.backpack.setStack(0,new ItemStack(Items.STONE_PICKAXE));
        a.backpack.setStack(1,new ItemStack(Items.STONE_SWORD));
        a.backpack.setStack(2,new ItemStack(Items.COBBLESTONE,2));
        a.backpack.setStack(3,new ItemStack(Items.OAK_PLANKS,8));
        a.backpack.setStack(4,new ItemStack(Items.STICK,4));
        BlockPos bench=c.getAbsolutePos(new BlockPos(3,2,2));
        c.getWorld().setBlockState(bench,Blocks.CRAFTING_TABLE.getDefaultState());
        OwnedBlocks.get(c.getWorld()).claim(bench,a.getUuid());
        NbtCompound owned=new NbtCompound();owned.putLong("OwnedBench",bench.asLong());
        a.crafting().readNbt(owned);
        for(int z=0;z<8;z++)c.setBlockState(new BlockPos(5,2,z),Blocks.STONE);
        c.runAtTick(1120,()->{
            c.assertTrue(owns(a,Items.STONE_AXE),
                "Bench recovery starved final real-stone upgrade"+debug(a)
                    +" crafting="+a.crafting().description()
                    +" benchBlocking="+a.crafting().nearbyBenchObstruction()
                    +" stone="+a.gathering().stoneDiagnostics(c.getAbsolutePos(new BlockPos(5,2,2))));
            c.assertTrue(ResourcePlan.stoneNeeded(a)==0,
                "Autonomous stone dependency still unmet"+debug(a));
            owner.discard();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=1100)
    public void brokenBenchDropCannotInterruptStoneExpedition(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.GUARD);
        // The next upgrade is affordable, but the old station has already
        // become a real drop outside the covered work area. A cleanup detour
        // must not replace the still-active expedition with a crafting turn.
        Item[] items={Items.WOODEN_PICKAXE,Items.WOODEN_AXE,Items.STONE_SWORD,
            Items.COBBLESTONE,Items.OAK_PLANKS,Items.STICK};
        int[] counts={1,1,1,3,8,6};
        for(int i=0;i<items.length;i++)a.backpack.setStack(i,new ItemStack(items[i],counts[i]));
        BlockPos bench=c.getAbsolutePos(new BlockPos(3,2,0));
        NbtCompound owned=new NbtCompound();owned.putLong("OwnedBench",bench.asLong());
        a.crafting().readNbt(owned);
        ItemEntity drop=new ItemEntity(c.getWorld(),bench.getX()+0.5,bench.getY()-2,bench.getZ()-1.5,
            new ItemStack(Items.CRAFTING_TABLE));
        drop.setToDefaultPickupDelay();drop.addCommandTag("aiplayer_drop_"+a.getUuid());
        c.getWorld().spawnEntity(drop);
        for(int z=2;z<=4;z++)c.setBlockState(new BlockPos(5,2,z),Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,3,false);
        boolean[] finished={false};
        c.runAtEveryTick(()->{
            if(a.gathering().active()){
                c.assertTrue(SurfaceStoneGathering.caveAccess(a,a.getBlockPos()),
                    "Bench cleanup pulled an unfinished expedition outside the cave"+debug(a));
                c.assertTrue(a.intelligence().activity()!=dev.aiplayer.ai.brain.DecisionPolicy.Activity.CRAFT,
                    "A broken bench drop was treated as a ready crafting station"+debug(a));
            } else if(a.gathering().progress()==3)finished[0]=true;
        });
        c.runAtTick(1000,()->{
            c.assertTrue(finished[0],"Stone collection was cancelled to recover its broken bench"+debug(a));
            c.assertTrue(owns(a,Items.STONE_PICKAXE) && owns(a,Items.STONE_AXE),
                "Deferred bench recovery lost the real station or the upgrade ingredients"+debug(a)
                    +" "+a.crafting().diagnostics());
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=500)
    public void refusesOutdoorOrderEvenWithNearbyCoveredStone(TestContext c){
        CompanionEntity a=worker(c);a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));c.setBlockState(new BlockPos(4,2,2),Blocks.STONE);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,10,z),Blocks.AIR);
        for(int x=3;x<=5;x++)for(int z=1;z<=3;z++)c.setBlockState(new BlockPos(x,5,z),Blocks.OAK_PLANKS);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(450,()->{c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,2,2))).isOf(Blocks.STONE),"Entered cave mining");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=160)
    public void detectsStoneThirtyBlocksAway(TestContext c){
        CompanionEntity a=worker(c);a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        // Preserve the full 32-block search distance, but give the pathfinder a
        // properly supported corridor instead of a 5-wide platform ending in void.
        // This changes the artificial arena, not the reachability assertion.
        for(int x=0;x<36;x++)for(int z=0;z<13;z++){c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);c.setBlockState(new BlockPos(x,10,z),Blocks.DIRT);}
        BlockPos p=new BlockPos(32,2,2);c.setBlockState(p,Blocks.STONE);a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(130,()->{
            // TestContext relocates the arena to negative world Y. Compare two
            // absolute world coordinates, never an absolute Y against local 1.9.
            double floorY=c.getAbsolutePos(new BlockPos(2,2,2)).getY();
            c.assertTrue(a.getY()>=floorY-0.1,"Fell from long-distance mining corridor"+debug(a));
            c.assertTrue(a.intelligence().memory().nearest("surface_stone",c.getAbsolutePos(p),c.getWorld().getTime(),1).isPresent(),"Missed distant stone"+debug(a));c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface")
    public void refusesThinExposedBridgeForMining(TestContext c){
        CompanionEntity a=worker(c);
        // A one-block-wide approach beside two deep drops is not a safe work route.
        BlockPos feet=c.getAbsolutePos(new BlockPos(12,2,2));
        for(int x=10;x<=14;x++){
            c.setBlockState(new BlockPos(x,1,2),Blocks.DIRT);
            for(int z=1;z<=3;z++)if(z!=2)for(int y=-1;y<=1;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.AIR);
        }
        c.assertFalse(WorkNavigation.safeWorkFooting(a,feet),"Unsafe bridge accepted as stable mining footing");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=2400)
    public void emptyInventoryWoodThenStone(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.WAIT);
        for(BlockPos base:java.util.List.of(new BlockPos(4,2,2),new BlockPos(4,2,6),new BlockPos(7,2,6))){for(int y=0;y<3;y++)c.setBlockState(base.up(y),Blocks.OAK_LOG);c.setBlockState(base.up(3),Blocks.OAK_LEAVES);}
        BlockPos p=new BlockPos(7,2,3);c.setBlockState(p,Blocks.STONE);a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(2200,()->{c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1 && owns(a,Items.WOODEN_PICKAXE) && !a.gathering().active(),"Full sequence failed"+debug(a)+a.gathering().stoneDiagnostics(c.getAbsolutePos(p)));c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=650)
    public void stepsOffStoneBesideBenchBeforeMining(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos stone=new BlockPos(4,2,4),bench=stone.west();
        c.setBlockState(stone,Blocks.STONE);
        c.setBlockState(bench,Blocks.CRAFTING_TABLE);
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),c.getAbsolutePos(bench));
        Vec3d start=Vec3d.ofBottomCenter(c.getAbsolutePos(stone.up()));
        a.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        c.runAtTick(5,()->a.gathering().start(ResourceGathering.Kind.STONE,1,true));
        c.runAtTick(600,()->{
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1 && !a.gathering().active(),
                "Did not step down before mining its former support"+debug(a)
                    +a.gathering().stoneDiagnostics(c.getAbsolutePos(stone)));
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(bench)).isOf(Blocks.CRAFTING_TABLE),
                "Destroyed protected bench while leaving stone");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=650)
    public void stepsOffFractionalStoneEdgeBeforeMining(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos stone=new BlockPos(4,2,4),bench=stone.west();
        c.setBlockState(stone,Blocks.STONE);
        c.setBlockState(bench,Blocks.CRAFTING_TABLE);
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),c.getAbsolutePos(bench));
        Vec3d start=Vec3d.of(c.getAbsolutePos(stone.up())).add(0.5207607545,0,0.2849537777);
        a.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        c.runAtTick(5,()->a.gathering().start(ResourceGathering.Kind.STONE,1,true));
        c.runAtTick(600,()->{
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1 && !a.gathering().active(),
                "Did not step down before mining its former support"+debug(a)
                    +a.gathering().stoneDiagnostics(c.getAbsolutePos(stone)));
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(bench)).isOf(Blocks.CRAFTING_TABLE),
                "Destroyed protected bench while leaving stone");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=650)
    public void choosesAnotherGalleryApproachAroundProtectedBench(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos bench=new BlockPos(3,2,2),stone=new BlockPos(5,2,2);
        c.setBlockState(bench,Blocks.CRAFTING_TABLE);
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),c.getAbsolutePos(bench));
        c.setBlockState(stone,Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(600,()->{
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1 && !a.gathering().active(),
                "Did not walk around blocked approach"+debug(a));
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(bench)).isOf(Blocks.CRAFTING_TABLE),
                "Destroyed protected bench to reach stone");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=1100)
    public void leavesStumpToPlaceBenchAndResumeStoneOrder(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.WAIT);
        BlockPos stump=new BlockPos(2,2,2);c.setBlockState(stump,Blocks.OAK_LOG);
        Vec3d start=Vec3d.ofBottomCenter(c.getAbsolutePos(stump.up()));
        a.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        a.backpack.setStack(0,new ItemStack(Items.OAK_LOG,6));
        a.backpack.setStack(1,new ItemStack(Items.WOODEN_AXE));
        a.backpack.setStack(2,new ItemStack(Items.WOODEN_SWORD));
        c.setBlockState(new BlockPos(5,2,2),Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(1000,()->{
            c.assertTrue(owns(a,Items.WOODEN_PICKAXE) && a.backpack.count(Items.COBBLESTONE)==1
                && !a.gathering().active(),"Stump blocked bench/pick/mining sequence"+debug(a));
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(stump)).isOf(Blocks.OAK_LOG),
                "Destroyed unrelated stump instead of stepping off");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=80)
    public void pickDependencyResumesEvenWithOptionalCraftSession(TestContext c){
        CompanionEntity a=worker(c);a.autoCraft=true;a.setMode(Mode.WAIT);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(5,()->{
            c.assertTrue(a.gathering().kind()==ResourceGathering.Kind.WOOD,"Missing wood prerequisite");
            a.backpack.setStack(0,new ItemStack(Items.WOODEN_PICKAXE));
            a.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,8));
            a.backpack.setStack(2,new ItemStack(Items.STICK,4));
            a.backpack.setStack(3,new ItemStack(Items.CRAFTING_TABLE));
            a.crafting().tick();
            c.assertTrue(a.crafting().isWorking(),"Fixture must have an optional craft session");
        });
        c.runAtTick(7,()->{
            c.assertTrue(a.gathering().kind()==ResourceGathering.Kind.STONE && a.gathering().isManual(),
                "Optional crafting delayed resuming the manual stone order"+debug(a));
            c.assertTrue(a.gathering().goal()==1,"Parent quantity changed");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="surface",tickLimit=60)
    public void surfaceOrderDoesNotDigOrConsumePick(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,10,z),Blocks.AIR);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos stone=new BlockPos(4,2,2);c.setBlockState(stone,Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(40,()->{
            c.assertTrue(a.gathering().active(),"Outdoor manual order was discarded instead of looking for a cave");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(stone)).isOf(Blocks.STONE),"Outdoor stone was mined");
            c.assertTrue(a.getMainHandStack().getDamage()==0,"Pick was used outdoors");
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==0,"Invented outdoor resource");
            c.complete();
        });
    }
}
