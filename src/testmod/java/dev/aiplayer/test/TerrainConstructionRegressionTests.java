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
public final class TerrainConstructionRegressionTests implements FabricGameTest {
    private void survey(TestContext c,int variant){
        V070Fixtures.floor(c,24,0,Blocks.STONE);BlockPos origin=c.getAbsolutePos(new BlockPos(8,2,8));
        int peak=0;
        for(int x=0;x<5;x++)for(int z=0;z<7;z++){
            int h=switch(variant){case 0->1;case 1->x<3?1:4;case 2->z<4?1:5;case 3->x==2 && z==3?7:1;case 4->x==2 && z==3?1:7;case 5->(x+z)%3+1;case 6->x+1;default->z+1;};peak=Math.max(peak,h);
            for(int y=1;y<=h;y++)c.getWorld().setBlockState(origin.add(x,y-2,z),Blocks.DIRT.getDefaultState());
            c.getWorld().setBlockState(origin.add(x,h-1,z),Blocks.GRASS.getDefaultState());
        }
        SiteTerrainPlan plan=SiteTerrainPlan.survey(c.getWorld(),origin,5,7,5);
        c.assertTrue(plan!=null && plan.floor().getX()==origin.getX() && plan.floor().getZ()==origin.getZ(),"Survey moved confirmed horizontal location");
        c.assertTrue(plan.floor().getY()>=origin.getY() && plan.floor().getY()<=origin.getY()+peak,"Survey escaped real contour");
        if(variant==3)c.assertTrue(plan.floor().getY()==origin.getY(),"One mound raised the entire house");
        if(variant==4)c.assertTrue(plan.floor().getY()==origin.getY()+6,"One deep depression lowered the entire house");
        var cells=plan.clearance(5,7,1,1);for(int i=1;i<cells.size();i++)c.assertTrue(cells.get(i).getY()<=cells.get(i-1).getY(),"Clearance undermines higher terrain");
        int highest=peak;
        c.assertTrue(cells.stream().anyMatch(p->p.getY()>=origin.getY()+highest-1),"Higher contour omitted from clearing");c.complete();
    }
    private void clear(TestContext c,String material,boolean protectedCell){
        V070Fixtures.floor(c,18,1,Blocks.DIRT);var npc=V070Fixtures.worker(c,new BlockPos(4,2,6));
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE));npc.backpack.setStack(0,new ItemStack(Items.DIAMOND_SHOVEL));
        BlockPos target=c.getAbsolutePos(new BlockPos(8,2,6));c.getWorld().setBlockState(target,V070Fixtures.block(material).getDefaultState());
        if(protectedCell)OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),target);
        var work=new ProjectWork(npc);boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=work.safeMine(target);});
        c.runAtTick(320,()->{
            c.assertTrue(protectedCell?!done[0] && !c.getWorld().getBlockState(target).isAir():done[0] && c.getWorld().getBlockState(target).isAir(),"Terrain clearing/protection failed: "+material+" "+work.diagnostics());c.complete();
        });
    }
    private void deepFoundation(TestContext c,int depth){
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)for(int y=0;y<=10;y++)c.setBlockState(new BlockPos(x,y,z),y==0?Blocks.STONE:Blocks.DIRT);
        // A narrow depression below a mostly high site must receive real fill,
        // not a floating floor or the old fixed four-block rejection.
        BlockPos bottom=c.getAbsolutePos(new BlockPos(10,11-depth,8));
        for(int y=11-depth;y<=10;y++)c.getWorld().setBlockState(new BlockPos(bottom.getX(),c.getAbsolutePos(new BlockPos(0,y,0)).getY(),bottom.getZ()),Blocks.AIR.getDefaultState());
        var npc=V070Fixtures.worker(c,new BlockPos(6,11,8));npc.backpack.setStack(0,new ItemStack(Items.COBBLESTONE,depth));npc.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,48));npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SHOVEL));
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(new BlockPos(8,11,8)),Items.OAK_PLANKS);
        c.runAtTick(2200,()->{
            for(int y=0;y<depth;y++)c.assertTrue(c.getWorld().getBlockState(bottom.up(y)).isOf(Blocks.COBBLESTONE),"Foundation gap at depth "+depth+": "+npc.tasks().diagnostics());
            c.assertTrue(npc.backpack.count(Items.COBBLESTONE)==0 && npc.tasks().progress()>0,"Fill was not paid before building floor");npc.tasks().cancel();c.complete();
        });
    }
    private void palette(TestContext c,String wood){
        var palette=HouseBlueprint.palette(wood);var plan=HouseBlueprint.plan(c.getAbsolutePos(new BlockPos(6,2,6)),palette);
        c.assertTrue(plan.stream().filter(HouseBlueprint.Part::door).count()==1,"House needs one actual door");
        c.assertTrue(plan.stream().anyMatch(p->p.item()==Items.GLASS) && plan.stream().anyMatch(p->p.item()==Items.FURNACE) && plan.stream().anyMatch(p->p.item()==Items.CRAFTING_TABLE),"Missing furnishing/window dependency");
        for(var p:plan)if(p.item()!=Items.GLASS && p.item()!=Items.FURNACE && p.item()!=Items.CRAFTING_TABLE)c.assertTrue(Registries.ITEM.getId(p.item()).getPath().startsWith(wood+"_"),"Mixed house species: "+p.item());
        c.assertTrue(plan.stream().anyMatch(p->p.state().getBlock() instanceof StairsBlock) && plan.stream().anyMatch(p->p.state().getBlock() instanceof SlabBlock),"Roof detail omitted");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void flatSurvey(TestContext c){survey(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void eastTerraceSurvey(TestContext c){survey(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void southTerraceSurvey(TestContext c){survey(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void singleTallMoundCut(TestContext c){survey(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void singleDeepDepressionFill(TestContext c){survey(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void irregularSaddleSurvey(TestContext c){survey(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void continuousEastSlope(TestContext c){survey(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void continuousSouthSlope(TestContext c){survey(c,7);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void grassPhysicallyCleared(TestContext c){clear(c,"grass",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void snowPhysicallyCleared(TestContext c){clear(c,"snow",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void snow_blockPhysicallyCleared(TestContext c){clear(c,"snow_block",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void dirtPhysicallyCleared(TestContext c){clear(c,"dirt",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void stonePhysicallyCleared(TestContext c){clear(c,"stone",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void gravelPhysicallyCleared(TestContext c){clear(c,"gravel",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void sandPhysicallyCleared(TestContext c){clear(c,"sand",false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=400)
    public void dirtProtectedDuringTerrainWork(TestContext c){clear(c,"dirt",true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=2400)
    public void realFoundationDepth2(TestContext c){deepFoundation(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=2400)
    public void realFoundationDepth4(TestContext c){deepFoundation(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=2400)
    public void realFoundationDepth6(TestContext c){deepFoundation(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=2400)
    public void realFoundationDepth8(TestContext c){deepFoundation(c,8);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void oakHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"oak");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void spruceHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"spruce");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void birchHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"birch");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void jungleHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"jungle");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void acaciaHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"acacia");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void dark_oakHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"dark_oak");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void mangroveHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"mangrove");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void cherryHouseKeepsPaletteAndFurnishings(TestContext c){palette(c,"cherry");}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void surveyIgnoresTreeCanopyForFloor(TestContext c){V070Fixtures.floor(c,24,1,Blocks.DIRT);BlockPos origin=c.getAbsolutePos(new BlockPos(8,2,8));V070Fixtures.tree(c,origin.add(2,0,3),"spruce",9,1);var plan=SiteTerrainPlan.survey(c.getWorld(),origin,5,7,5);c.assertTrue(plan.floor().equals(origin),"Canopy became house floor");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_terrain",tickLimit=100)
    public void surveyIgnoresThinSnowForFloor(TestContext c){V070Fixtures.floor(c,24,1,Blocks.DIRT);BlockPos origin=c.getAbsolutePos(new BlockPos(8,2,8));for(int x=0;x<5;x++)for(int z=0;z<7;z++)c.getWorld().setBlockState(origin.add(x,0,z),Blocks.SNOW.getDefaultState());var plan=SiteTerrainPlan.survey(c.getWorld(),origin,5,7,5);c.assertTrue(plan.floor().equals(origin),"Thin snow became foundation height");c.complete();}
}
