package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.ai.navigation.PreparedRoute;
import dev.aiplayer.ai.tasks.*;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class ResourceAccessTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c,BlockPos at){
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,at);npc.autoSurvive=false;npc.autoCraft=true;npc.autoLoot=true;npc.autoEquip=true;
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));return npc;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=420)
    public void bridgesGapWithRealBlocksBeforeWalkingAcross(TestContext c){
        for(int x=1;x<12;x++)for(int z=3;z<=7;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            if(x!=5 && x!=6)c.setBlockState(new BlockPos(x,3,z),Blocks.STONE);
        }
        CompanionEntity npc=worker(c,new BlockPos(3,4,5));npc.backpack.setStack(0,new ItemStack(Items.DIRT,2));
        BlockPos goal=c.getAbsolutePos(new BlockPos(9,4,5));
        PreparedRoute route=new PreparedRoute(npc,p->p.getZ()==goal.getZ() && p.getY()>=goal.getY()-2);
        c.runAtEveryTick(()->{if(npc.age>4){if(route.active())route.tick();else route.begin(goal,0);}});
        c.runAtTick(360,()->{
            c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(goal))<1,"Did not cross real bridge: "+npc.getPos()+" "+route.status());
            c.assertTrue(npc.backpack.count(Items.DIRT)==0,"Bridge did not consume two real blocks");
            for(int x=5;x<=6;x++)c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,3,5))).isOf(Blocks.DIRT),"Missing bridge support");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=700)
    public void swimmingWorkerFindsDryExitThenContinuesProject(TestContext c){
        for(int x=0;x<14;x++)for(int z=0;z<14;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            if(x<4 || x>7 || z<4 || z>7)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            else c.setBlockState(new BlockPos(x,1,z),Blocks.WATER);
        }
        CompanionEntity npc=worker(c,new BlockPos(5,1,5));ProjectWork work=new ProjectWork(npc);
        BlockPos destination=c.getAbsolutePos(new BlockPos(10,2,5));boolean[] arrived={false};
        c.runAtEveryTick(()->{if(!arrived[0])arrived[0]=work.moveTo(destination);});
        c.runAtTick(620,()->{c.assertTrue(arrived[0] && !npc.isTouchingWater(),"Worker never left the water: "+work.diagnostics());c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=500)
    public void cutsNaturalPassageAndPhysicallyWalksThroughIt(TestContext c){
        for(int x=1;x<12;x++)for(int z=1;z<10;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(3,2,5));
        for(int z=1;z<10;z++)for(int y=2;y<6;y++)c.setBlockState(new BlockPos(5,y,z),Blocks.DIRT);
        BlockPos goal=c.getAbsolutePos(new BlockPos(8,2,5));
        // Constrain this fixture to the corridor so it requires an actual cut.
        PreparedRoute route=new PreparedRoute(npc,p->Math.abs(p.getZ()-goal.getZ())<=1 && p.getX()>=goal.getX()-6 && p.getX()<=goal.getX()+1);
        c.runAtEveryTick(()->{if(npc.age>4){if(route.active())route.tick();else route.begin(goal,0);}});
        c.runAtTick(440,()->{
            c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(goal))<1,"Did not traverse prepared passage: "+npc.getPos()+" "+route.status());
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(5,2,5))).isAir(),"Natural wall was not physically opened");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=120)
    public void routePreservesProtectedWallAndFluidPocket(TestContext c){
        for(int x=1;x<10;x++)c.setBlockState(new BlockPos(x,1,5),Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(3,2,5));BlockPos wall=c.getAbsolutePos(new BlockPos(5,2,5));
        for(int y=2;y<7;y++){BlockPos p=wall.up(y-2);c.getWorld().setBlockState(p,Blocks.DIRT.getDefaultState());OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),p);}
        c.getWorld().setBlockState(wall.east(),Blocks.WATER.getDefaultState());
        PreparedRoute route=new PreparedRoute(npc,p->p.getZ()==wall.getZ());
        c.runAtEveryTick(()->route.begin(wall.east(3),0));
        c.runAtTick(100,()->{c.assertTrue(c.getWorld().getBlockState(wall).isOf(Blocks.DIRT),"Protected wall was cut");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=1800)
    public void hiddenStoneUsesStairAndReturnsWithOnlySurfaceCapped(TestContext c){
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)for(int y=0;y<=8;y++)
            c.setBlockState(new BlockPos(x,y,z),y>=6?Blocks.DIRT:Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(12,9,12));ProjectMaterials materials=new ProjectMaterials(npc,new ProjectWork(npc));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        BlockPos entry=npc.getBlockPos();boolean[] done={false},underground={false};
        c.runAtEveryTick(()->{
            if(npc.age%100==0)System.out.println("ACCESS_STAIR age="+npc.age+" count="+materials.count(Items.COBBLESTONE)+" pos="+npc.getPos()+" "+materials.status()+" "+materials.diagnostics());
            npc.survival().tick();if(npc.getY()<entry.getY()-1)underground[0]=true;
            if(!done[0])done[0]=materials.ensure(Items.COBBLESTONE,4);
        });
        c.runAtTick(1680,()->{
            c.assertTrue(done[0] && materials.count(Items.COBBLESTONE)>=4,"Hidden stone was not delivered: "+materials.status()+" "+materials.diagnostics()+" pos="+npc.getPos());
            c.assertTrue(underground[0] && npc.getY()>=entry.getY()-0.2,"NPC did not descend and physically return");
            int lowerAir=0;for(int x=9;x<=16;x++)for(int z=9;z<=16;z++){
                c.assertTrue(!c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,8,z))).isAir(),"Surface entrance left open at "+x+",8,"+z);
                if(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,6,z))).isAir())lowerAir++;
            }
            c.assertTrue(lowerAir>0,"Restoration filled the underground staircase");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=700)
    public void projectEscapesDrainedPitAndResumesDestination(TestContext c){
        for(int x=0;x<14;x++)for(int z=0;z<14;z++)for(int y=0;y<5;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.DIRT);
        for(int y=1;y<5;y++)c.setBlockState(new BlockPos(5,y,5),Blocks.AIR);
        CompanionEntity npc=worker(c,new BlockPos(5,1,5));ProjectWork work=new ProjectWork(npc);BlockPos destination=c.getAbsolutePos(new BlockPos(10,5,5));
        boolean[] arrived={false};c.runAtEveryTick(()->{if(!arrived[0])arrived[0]=work.moveTo(destination);});
        c.runAtTick(630,()->{c.assertTrue(arrived[0],"Project remained trapped after water drained: "+work.diagnostics());c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=1800)
    public void buriedIronUsesSuitablePickAndSurvivesReloadDuringStair(TestContext c){
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)for(int y=0;y<=8;y++)
            c.setBlockState(new BlockPos(x,y,z),y>=6?Blocks.DIRT:y==5?Blocks.IRON_ORE:Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(12,9,12));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        ProjectMaterials[] materials={new ProjectMaterials(npc,new ProjectWork(npc))};
        BlockPos entry=npc.getBlockPos();boolean[] done={false},reloaded={false};
        c.runAtEveryTick(()->{
            if(!reloaded[0] && npc.getY()<entry.getY()-1 && materials[0].count(Items.RAW_IRON)==0){
                NbtCompound entity=new NbtCompound(),job=new NbtCompound();npc.writeCustomDataToNbt(entity);materials[0].writeNbt(job);
                c.assertTrue(job.getCompound("ProjectMaterials").contains("MaterialExcavation"),"Active stair was not saved");
                materials[0].pause();npc.readCustomDataFromNbt(entity);
                materials[0]=new ProjectMaterials(npc,new ProjectWork(npc));materials[0].readNbt(job);reloaded[0]=true;
            }
            if(!done[0])done[0]=materials[0].ensure(Items.RAW_IRON,2);
        });
        c.runAtTick(1680,()->{
            c.assertTrue(reloaded[0] && done[0] && materials[0].count(Items.RAW_IRON)>=2,
                "Buried iron/reload did not finish: "+materials[0].status()+" "+materials[0].diagnostics());
            c.assertTrue(npc.getMainHandStack().getDamage()>0,"Mining did not use a real suitable pick");
            c.assertTrue(npc.getY()>=entry.getY()-0.2 && !npc.intelligence().restoration().pending(),"Return/cleanup was lost on reload");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=1200)
    public void cancellingUndergroundSearchStillReturnsAndClosesEntrance(TestContext c){
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)for(int y=0;y<=8;y++)
            c.setBlockState(new BlockPos(x,y,z),y>=6?Blocks.DIRT:Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(12,9,12));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        ProjectMaterials materials=new ProjectMaterials(npc,new ProjectWork(npc));BlockPos entry=npc.getBlockPos();boolean[] cancelled={false};
        c.runAtEveryTick(()->{
            if(cancelled[0])return;
            if(npc.getY()<entry.getY()-1){materials.cancel();cancelled[0]=true;
                NbtCompound entity=new NbtCompound();npc.writeCustomDataToNbt(entity);npc.readCustomDataFromNbt(entity);
                c.assertTrue(npc.intelligence().restoration().pending(),"Cancel/reload discarded surface restoration");
            }else materials.ensure(Items.COBBLESTONE,16);
        });
        c.runAtTick(1120,()->{
            c.assertTrue(cancelled[0] && !npc.intelligence().restoration().pending() && npc.getY()>=entry.getY()-0.2,"Cancelled worker remained underground");
            for(int x=9;x<=16;x++)for(int z=9;z<=16;z++)c.assertTrue(!c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,8,z))).isAir(),"Cancelled stair left surface open");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=700)
    public void observedCaveIsPreferredBeforeStartingAnOutdoorStair(TestContext c){
        for(int x=0;x<24;x++)for(int z=0;z<16;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        for(int x=10;x<=18;x++)for(int z=3;z<=9;z++)c.setBlockState(new BlockPos(x,5,z),Blocks.STONE);
        c.setBlockState(new BlockPos(14,2,6),Blocks.COAL_ORE);
        CompanionEntity npc=worker(c,new BlockPos(5,2,6));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        ProjectMaterials materials=new ProjectMaterials(npc,new ProjectWork(npc));boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=materials.ensure(Items.COAL,1);});
        c.runAtTick(620,()->{
            c.assertTrue(done[0] && materials.count(Items.COAL)>0,"Did not enter observed cave and collect coal: "+materials.status());
            c.assertFalse(npc.intelligence().restoration().opened(),"Opened an unnecessary outdoor staircase instead of using cave");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(5,1,6))).isOf(Blocks.DIRT),"Destroyed original surface footing");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=900)
    public void missingBridgeBlocksArePhysicallyGatheredBeforeCrossing(TestContext c){
        for(int x=1;x<12;x++)for(int z=3;z<=7;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            if(x!=5 && x!=6)c.setBlockState(new BlockPos(x,3,z),Blocks.STONE);
        }
        c.setBlockState(new BlockPos(3,4,4),Blocks.DIRT);c.setBlockState(new BlockPos(2,4,4),Blocks.DIRT);
        // Pickup can leave the worker on its remaining source of soil. Start
        // there deliberately so leaving that perch is tested every run.
        CompanionEntity npc=worker(c,new BlockPos(3,5,4));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        BlockPos goal=c.getAbsolutePos(new BlockPos(9,4,5));
        PreparedRoute route=new PreparedRoute(npc,p->Math.abs(p.getZ()-goal.getZ())<=2 && p.getY()>=goal.getY()-2);
        c.runAtEveryTick(()->{if(npc.age>4){if(route.active())route.tick();else route.begin(goal,0);}});
        c.runAtTick(820,()->{
            c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(goal))<1,"Did not gather bridge prerequisites: "+npc.getPos()+" "+route.diagnostics()+" inventory="+npc.backpack+" hand="+npc.getMainHandStack()+" soil="+c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(3,4,4)))+"/"+c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(2,4,4)))+" drops="+c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,npc.getBoundingBox().expand(8),e->e.isAlive()).stream().map(e->e.getStack()+"@"+e.getPos()).toList());
            int placed=0;for(int x=5;x<=6;x++)for(int z=3;z<=7;z++)if(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,3,z))).isOf(Blocks.DIRT))placed++;
            c.assertTrue(placed==2 && c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(3,4,4))).isAir()
                && c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(2,4,4))).isAir(),"Bridge did not use the two mined soil blocks");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=600)
    public void deferringAnUnreachableWorkbenchDoesNotCrashNavigation(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,5));npc.setAiDisabled(true);
        BlockPos bench=c.getAbsolutePos(new BlockPos(28,2,5));c.getWorld().setBlockState(bench,Blocks.CRAFTING_TABLE.getDefaultState());OwnedBlocks.get(c.getWorld()).claim(bench,npc.getUuid());
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());saved.putLong("OwnedBench",bench.asLong());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);npc.autoCraft=false;
        c.runAtEveryTick(()->{if(npc.age>=3)npc.crafting().tick();});
        c.runAtTick(550,()->{NbtCompound state=new NbtCompound();npc.crafting().writeNbt(state);
            c.assertTrue(state.getLongArray("DeferredOwnedBenches").length==1,"Unreachable bench was not deferred after bounded retries");
            c.assertTrue(c.getWorld().getBlockState(bench).isOf(Blocks.CRAFTING_TABLE),"Unreachable bench was removed remotely");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="resource_access",tickLimit=3800)
    public void buriedIronCraftsStonePickFromItsOwnUndergroundStoneDependency(TestContext c){
        for(int x=0;x<48;x++)for(int z=0;z<32;z++)for(int y=0;y<=8;y++)
            c.setBlockState(new BlockPos(x,y,z),y>=6?Blocks.DIRT:y==5 && x>=20?Blocks.IRON_ORE:Blocks.STONE);
        CompanionEntity npc=worker(c,new BlockPos(12,9,12));npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG,4));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);saved.putUuid("Owner",java.util.UUID.randomUUID());npc.readCustomDataFromNbt(saved);npc.setMode(Mode.WAIT);
        ProjectMaterials materials=new ProjectMaterials(npc,new ProjectWork(npc));boolean[] done={false},stoneCollected={false};
        c.runAtEveryTick(()->{
            if(materials.count(Items.COBBLESTONE)>=3)stoneCollected[0]=true;
            if(!done[0])done[0]=materials.ensure(Items.RAW_IRON,1);
        });
        c.runAtTick(3680,()->{
            c.assertTrue(done[0] && stoneCollected[0] && materials.count(Items.RAW_IRON)==1 && materials.count(Items.STONE_PICKAXE)==1,
                "Iron request cancelled its own underground tool prerequisite: "+materials.status()+" "+materials.diagnostics());
            c.assertTrue(materials.count(Items.COBBLESTONE)==0,"Stone pick did not consume its three real cobblestones");
            c.assertFalse(npc.intelligence().restoration().pending(),"Tool/ore subtrips did not complete restoration");c.complete();
        });
    }

}
