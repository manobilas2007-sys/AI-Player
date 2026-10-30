package dev.aiplayer.test;
import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.ai.tasks.*;
import dev.aiplayer.ai.navigation.*;
import dev.aiplayer.compat.FallingTreeBridge;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import java.util.*;
public final class QuarryExcavationRegressionTests implements FabricGameTest {
    private void quarry(TestContext c,int variant){
        V070Fixtures.floor(c,24,0,Blocks.STONE);V070Fixtures.floor(c,24,1,Blocks.DIRT);
        Block sand=variant==2?Blocks.RED_SAND:Blocks.SAND;Item item=sand.asItem();int height=variant==1?5:3,columns=variant==3?2:1;
        BlockPos base=c.getAbsolutePos(new BlockPos(10,2,6));
        for(int x=0;x<columns;x++)for(int y=0;y<height;y++)c.getWorld().setBlockState(base.add(x,y,0),sand.getDefaultState());
        if(variant==4){c.getWorld().setBlockState(base.east(),Blocks.WATER.getDefaultState());}
        var npc=V070Fixtures.worker(c,new BlockPos(6,2,6));npc.autoCraft=true;npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SHOVEL));
        if(variant==4)npc.backpack.setStack(0,new ItemStack(Items.DIRT,8));
        ProjectMaterials[] m={new ProjectMaterials(npc,new ProjectWork(npc))};boolean[] done={false},reloaded={false};
        c.runAtEveryTick(()->{
            if(!done[0])done[0]=m[0].ensure(item,height*columns);
            if((variant==1 || variant==4) && npc.age%200==0 && !done[0])System.out.println("QUARRY v="+variant+" tick="+npc.age+" status="+m[0].status()+" "+m[0].diagnostics());
            if(variant==5 && !reloaded[0] && m[0].count(item)>0){NbtCompound data=new NbtCompound();m[0].writeNbt(data);m[0].pause();m[0]=new ProjectMaterials(npc,new ProjectWork(npc));m[0].readNbt(data);reloaded[0]=true;}
        });
        c.runAtTick(1600,()->{
            c.assertTrue(done[0] && m[0].count(item)==height*columns,"Quarry did not collect its entire real column: "+m[0].diagnostics());
            c.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Gravity/shore damaged the worker");
            if(variant==4)c.assertTrue(npc.backpack.count(Items.DIRT)<8,"Shore quarry ignored containment materials");
            if(variant==5)c.assertTrue(reloaded[0],"No mid-quarry reload occurred");c.complete();
        });
    }
    private void pit(TestContext c,int variant){
        Block block=variant==2?Blocks.GRAVEL:variant==3?Blocks.RED_SAND:Blocks.SAND;int depth=variant==1?4:3;
        for(int x=0;x<18;x++)for(int z=0;z<18;z++)for(int y=0;y<=depth;y++)c.setBlockState(new BlockPos(x,y,z),y==0?Blocks.STONE:block);
        int width=variant==4?2:1;for(int x=5;x<5+width;x++)for(int z=5;z<5+width;z++)for(int y=1;y<=depth;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.AIR);
        var npc=V070Fixtures.worker(c,new BlockPos(5,1,5));npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SHOVEL));
        var work=new ProjectWork(npc);BlockPos goal=c.getAbsolutePos(new BlockPos(12,depth+1,5));boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=work.moveTo(goal);});
        c.runAtTick(1200,()->{c.assertTrue(done[0],"Gravity pit did not recover: "+work.diagnostics());c.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Recovery suffocated worker");c.complete();});
    }
    private void stair(TestContext c,int variant){
        for(int x=0;x<28;x++)for(int z=0;z<28;z++)for(int y=0;y<=10;y++)c.setBlockState(new BlockPos(x,y,z),y>=7?Blocks.DIRT:Blocks.STONE);
        var npc=V070Fixtures.worker(c,new BlockPos(12,11,12));npc.autoCraft=true;npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        if(variant==1)for(int x=0;x<28;x++)for(int z=0;z<28;z++)c.setBlockState(new BlockPos(x,5,z),Blocks.IRON_ORE);
        if(variant==2)for(int x=0;x<28;x++)for(int z=0;z<28;z++)c.setBlockState(new BlockPos(x,5,z),Blocks.COAL_ORE);
        Item item=variant==1?Items.RAW_IRON:variant==2?Items.COAL:Items.COBBLESTONE;int amount=variant==3?8:3;
        var m=new ProjectMaterials(npc,new ProjectWork(npc));BlockPos entry=npc.getBlockPos();boolean[] down={false},done={false},saved={false};
        c.runAtEveryTick(()->{
            if(npc.getY()<entry.getY()-1)down[0]=true;
            if(variant==4 && down[0] && !saved[0]){NbtCompound data=new NbtCompound();npc.writeCustomDataToNbt(data);npc.readCustomDataFromNbt(data);saved[0]=true;}
            if(!done[0])done[0]=m.ensure(item,amount);
        });
        c.runAtTick(2600,()->{
            c.assertTrue(done[0] && down[0] && m.count(item)>=amount,"Underground batch failed: "+m.diagnostics());
            c.assertTrue(npc.getY()>=entry.getY()-0.2 && !npc.intelligence().restoration().pending(),"Did not physically return and restore surface");
            c.assertTrue(npc.getMainHandStack().getDamage()>0,"No real tool wear underground");c.complete();
        });
    }
    private void excavationGuard(TestContext c,int variant){
        for(int x=0;x<18;x++)for(int z=0;z<18;z++)for(int y=0;y<7;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.DIRT);
        var npc=V070Fixtures.worker(c,new BlockPos(8,7,8));var digging=new SafeExcavation(npc,new PhysicalMining(npc));BlockPos entry=npc.getBlockPos();
        for(Direction side:Direction.Type.HORIZONTAL){BlockPos p=entry.offset(side);
            if(variant==0)OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),p.down());
            if(variant==1)c.getWorld().setBlockState(p.down(2),Blocks.AIR.getDefaultState());
            if(variant==2)c.getWorld().setBlockState(p,Blocks.WATER.getDefaultState());
            if(variant==3){c.getWorld().setBlockState(p,Blocks.DIRT.getDefaultState());c.getWorld().setBlockState(p.up(),Blocks.SAND.getDefaultState());}
            if(variant==4)c.getWorld().setBlockState(p.down(),Blocks.CHEST.getDefaultState());
        }
        c.runAtTick(5,()->{c.assertFalse(digging.beginStair(),"Unsafe staircase accepted: "+variant);c.assertTrue(c.getWorld().getBlockState(entry.down()).isOf(Blocks.DIRT),"Excavation removed current footing");c.complete();});
    }
    private void restoration(TestContext c,int variant){
        V070Fixtures.floor(c,18,0,Blocks.STONE);V070Fixtures.floor(c,18,1,Blocks.DIRT);var npc=V070Fixtures.worker(c,new BlockPos(5,2,5));
        BlockPos entry=npc.getBlockPos(),surface=entry.east().down(),lower=surface.down();
        var repair=npc.intelligence().restoration();repair.begin(entry);
        c.getWorld().setBlockState(surface,Blocks.AIR.getDefaultState());repair.removed(surface);
        c.getWorld().setBlockState(lower,Blocks.AIR.getDefaultState());repair.removed(lower);npc.backpack.setStack(0,new ItemStack(Items.DIRT,2));
        if(variant==1){NbtCompound n=new NbtCompound();repair.write(n);repair.read(n);}
        if(variant==2){c.getWorld().setBlockState(surface,Blocks.BRICKS.getDefaultState());OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),surface);}
        if(variant==3){NbtCompound n=new NbtCompound();repair.write(n);n.getCompound("SurfaceRestoration").putString("Dimension","minecraft:the_nether");repair.read(n);c.assertFalse(repair.opened(),"Cross-dimension obligation remained");c.complete();return;}
        repair.requestReturn();
        c.runAtTick(380,()->{c.assertFalse(repair.pending(),"Surface repair remained blocked: "+repair.diagnostics());c.assertTrue(c.getWorld().getBlockState(lower).isAir(),"Repair filled the underground tunnel");c.assertTrue(c.getWorld().getBlockState(surface).isOf(variant==2?Blocks.BRICKS:Blocks.DIRT),"Surface repair ignored real soil/player edit");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void sandColumnBatch(TestContext c){quarry(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void tallSandColumnTopDown(TestContext c){quarry(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void redSandExactDrops(TestContext c){quarry(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void twoSandColumnsOneTrip(TestContext c){quarry(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void shoreWaterContainedBeforeQuarry(TestContext c){quarry(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1800)
    public void quarryResumesAfterSave(TestContext c){quarry(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1400)
    public void threeDeepSandPitEscape(TestContext c){pit(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1400)
    public void fourDeepSandPitEscape(TestContext c){pit(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1400)
    public void gravelPitEscape(TestContext c){pit(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1400)
    public void redSandPitEscape(TestContext c){pit(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=1400)
    public void wideSandPitEscape(TestContext c){pit(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=2800)
    public void buriedStoneBatchPickup(TestContext c){stair(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=2800)
    public void buriedIronFocusedStair(TestContext c){stair(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=2800)
    public void buriedCoalFocusedStair(TestContext c){stair(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=2800)
    public void largerStoneBatchRestoresEntrance(TestContext c){stair(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=2800)
    public void stairEntityReloadRetainsReturn(TestContext c){stair(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void stairRejectsProtectedSurface(TestContext c){excavationGuard(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void stairRejectsUnsupportedStep(TestContext c){excavationGuard(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void stairRejectsWaterPocket(TestContext c){excavationGuard(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void stairRejectsFallingCeiling(TestContext c){excavationGuard(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void stairRejectsContainer(TestContext c){excavationGuard(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=450)
    public void onlyUpperSoilIsRestored(TestContext c){restoration(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=450)
    public void restorationSurvivesReload(TestContext c){restoration(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=450)
    public void restorationPreservesLaterPlayerEdit(TestContext c){restoration(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=450)
    public void restorationRejectsOtherDimension(TestContext c){restoration(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void quarryNeverMinesOwnFooting(TestContext c){V070Fixtures.floor(c,16,0,Blocks.STONE);V070Fixtures.floor(c,16,1,Blocks.SAND);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));var work=new ProjectWork(n);BlockPos footing=n.getBlockPos().down();c.runAtTick(5,()->{work.safeMine(footing);c.assertTrue(c.getWorld().getBlockState(footing).isOf(Blocks.SAND),"Quarry removed occupied footing");c.complete();});}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=180)
    public void wetQuarrySealConsumesRealBlock(TestContext c){V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos wet=c.getAbsolutePos(new BlockPos(6,1,4));c.getWorld().setBlockState(wet,Blocks.WATER.getDefaultState());n.backpack.setStack(0,new ItemStack(Items.DIRT));var work=new ProjectWork(n);boolean[] done={false};c.runAtEveryTick(()->{if(!done[0])done[0]=work.sealWater(wet,Items.DIRT);});c.runAtTick(120,()->{c.assertTrue(done[0] && n.backpack.count(Items.DIRT)==0 && c.getWorld().getBlockState(wet).isOf(Blocks.DIRT),"Water seal was not a paid physical placement");c.complete();});}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void wetQuarrySealRequiresInventory(TestContext c){V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos wet=c.getAbsolutePos(new BlockPos(6,1,4));c.getWorld().setBlockState(wet,Blocks.WATER.getDefaultState());var work=new ProjectWork(n);c.assertFalse(work.sealWater(wet,Items.DIRT),"Invented containment block");c.assertTrue(!c.getWorld().getFluidState(wet).isEmpty(),"Removed water without material");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void wetQuarrySealPreservesProtectedCell(TestContext c){V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos wet=c.getAbsolutePos(new BlockPos(6,1,4));c.getWorld().setBlockState(wet,Blocks.WATER.getDefaultState());OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),wet);n.backpack.setStack(0,new ItemStack(Items.DIRT));c.assertFalse(new ProjectWork(n).sealWater(wet,Items.DIRT),"Seal overwrote protected cell");c.assertTrue(n.backpack.count(Items.DIRT)==1,"Failed seal consumed material");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=100)
    public void quarrySealDoesNotReplaceLava(TestContext c){V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos wet=c.getAbsolutePos(new BlockPos(6,1,4));c.getWorld().setBlockState(wet,Blocks.LAVA.getDefaultState());n.backpack.setStack(0,new ItemStack(Items.DIRT));c.assertFalse(new ProjectWork(n).sealWater(wet,Items.DIRT),"Water handler treated lava as safe water");c.complete();}

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_quarries",tickLimit=200)
    public void containmentPlacementWorksWhileSwimming(TestContext c){
        V070Fixtures.floor(c,16,1,Blocks.STONE);
        for(int x=3;x<=7;x++)for(int z=3;z<=7;z++)c.setBlockState(new BlockPos(x,2,z),Blocks.WATER);
        var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.backpack.setStack(0,new ItemStack(Items.DIRT));var work=new ProjectWork(n);BlockPos target=c.getAbsolutePos(new BlockPos(6,2,4));boolean[] done={false},wet={false};
        c.runAtEveryTick(()->{if(n.age<10 || done[0])return;wet[0]|=n.isTouchingWater();done[0]=work.sealWater(target,Items.DIRT);});
        c.runAtTick(140,()->{c.assertTrue(wet[0] && done[0] && n.backpack.count(Items.DIRT)==0 && c.getWorld().getBlockState(target).isOf(Blocks.DIRT),"Swimming recovery preempted actual containment placement: "+work.diagnostics());c.complete();});
    }
}
