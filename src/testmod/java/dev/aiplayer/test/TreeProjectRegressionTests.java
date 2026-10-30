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
public final class TreeProjectRegressionTests implements FabricGameTest {
    private void fell(TestContext c,String species,int height,int width,int variant){
        V070Fixtures.floor(c,18,1,Blocks.DIRT);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(3,2,5));npc.autoLoot=false;
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));BlockPos base=c.getAbsolutePos(new BlockPos(6,2,5));
        V070Fixtures.tree(c,base,species,height,width);int count=height*width*width;
        if(variant==1){c.getWorld().setBlockState(base.down(),Blocks.PODZOL.getDefaultState());}
        if(variant==2){c.getWorld().setBlockState(base.down(),Blocks.ROOTED_DIRT.getDefaultState());}
        if(variant==3){npc.treeHarvest().observe(base);NbtCompound n=new NbtCompound();npc.treeHarvest().write(n);npc.treeHarvest().read(n);}
        if(variant==4){npc.treeHarvest().observe(base);c.getWorld().setBlockState(base,Blocks.AIR.getDefaultState());base=base.up();count--;}
        if(variant==5){npc.backpack.setStack(0,new ItemStack(V070Fixtures.item(species+"_log"),60));}
        Item item=V070Fixtures.item(species+"_log");int before=npc.backpack.count(item);
        c.assertFalse(npc.gathering().active(),"Tree fixture accidentally depends on a direct WOOD order");
        c.assertTrue(FallingTreeBridge.tryFell(npc,base),"Shared FallingTree session refused "+species+" variant="+variant);
        c.assertTrue(V070Fixtures.drops(c,base,item)==count,"Log drops duplicated/lost");
        c.assertTrue(npc.backpack.count(item)==before,"Tree inserted remote inventory");
        c.assertTrue(npc.getMainHandStack().getDamage()>0,"Batch had no real tool cost");c.complete();
    }
    private void reject(TestContext c,int reason){
        V070Fixtures.floor(c,16,1,Blocks.DIRT);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(3,2,5));
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));BlockPos base=c.getAbsolutePos(new BlockPos(6,2,5));V070Fixtures.tree(c,base,"spruce",5,1);
        if(reason==0)OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),base.up(3));
        if(reason==1)OwnedBlocks.get(c.getWorld()).claim(base.up(2),UUID.randomUUID());
        if(reason==2)npc.getMainHandStack().setDamage(npc.getMainHandStack().getMaxDamage()-1);
        if(reason==3)npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        if(reason==4)for(int i=0;i<npc.backpack.size();i++)npc.backpack.setStack(i,new ItemStack(Items.STONE,64));
        if(reason==5)c.getWorld().setBlockState(base.down(),Blocks.STONE.getDefaultState());
        if(reason==6)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)c.getWorld().setBlockState(base.add(x,5,z),Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT,true));
        if(reason==7){npc.treeHarvest().observe(base);OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),base.up(4));}
        c.assertFalse(FallingTreeBridge.tryFell(npc,base),"Rejected tree batch modified terrain: "+reason);
        c.assertTrue(c.getWorld().getBlockState(base).isOf(Blocks.SPRUCE_LOG) && V070Fixtures.drops(c,base,Items.SPRUCE_LOG)==0,"Preflight was not atomic");c.complete();
    }
    private void project(TestContext c,String species,int amount,boolean reload){
        V070Fixtures.floor(c,24,1,Blocks.DIRT);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(4,2,6));npc.autoCraft=true;
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));BlockPos base=c.getAbsolutePos(new BlockPos(10,2,6));V070Fixtures.tree(c,base,species,6,1);
        Item log=V070Fixtures.item(species+"_log"),planks=V070Fixtures.item(species+"_planks");
        ProjectMaterials[] materials={new ProjectMaterials(npc,new ProjectWork(npc))};materials[0].selectWood(log,planks);materials[0].demand(Map.of(planks,amount));
        boolean[] sawBatch={false},done={false};
        c.runAtEveryTick(()->{
            if(c.getWorld().getBlockState(base).isAir() && c.getWorld().getBlockState(base.up(5)).isAir())sawBatch[0]=true;
            if(!done[0])done[0]=materials[0].ensureForProject(planks,1);
        });
        if(reload)c.runAtTick(35,()->{NbtCompound n=new NbtCompound();materials[0].writeNbt(n);materials[0].pause();materials[0]=new ProjectMaterials(npc,new ProjectWork(npc));materials[0].readNbt(n);});
        c.runAtTick(700,()->{c.assertTrue(done[0] && sawBatch[0] && materials[0].count(planks)>=amount,"Project did not fell and craft a whole batch: "+materials[0].diagnostics());c.assertFalse(npc.gathering().active(),"Project leaked a direct wood order");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void oakProjectTreeDetection(TestContext c){fell(c,"oak",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void spruceProjectTreeDetection(TestContext c){fell(c,"spruce",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void birchProjectTreeDetection(TestContext c){fell(c,"birch",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void jungleProjectTreeDetection(TestContext c){fell(c,"jungle",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void acaciaProjectTreeDetection(TestContext c){fell(c,"acacia",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void dark_oakProjectTreeDetection(TestContext c){fell(c,"dark_oak",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void mangroveProjectTreeDetection(TestContext c){fell(c,"mangrove",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void cherryProjectTreeDetection(TestContext c){fell(c,"cherry",5,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void giantSpruce(TestContext c){fell(c,"spruce",9,2,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void tallJungle(TestContext c){fell(c,"spruce",14,1,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void podzolRoot(TestContext c){fell(c,"spruce",7,1,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void rootedDirtBase(TestContext c){fell(c,"spruce",6,1,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void rememberedCutRoot(TestContext c){fell(c,"spruce",7,1,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void partialStackCapacity(TestContext c){fell(c,"spruce",8,1,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void protectedCrown(TestContext c){reject(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void foreignClaim(TestContext c){reject(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void almostBrokenAxe(TestContext c){reject(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void wrongTool(TestContext c){reject(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void fullBackpack(TestContext c){reject(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void rootlessLogs(TestContext c){reject(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void decorativeLeaves(TestContext c){reject(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void protectedAfterObservation(TestContext c){reject(c,7);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void treeMemorySurvivesReload(TestContext c){fell(c,"oak",8,1,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void spruceMemorySurvivesReload(TestContext c){fell(c,"spruce",9,1,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=100)
    public void twoByTwoRemnant(TestContext c){fell(c,"dark_oak",6,2,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=800)
    public void oakProjectBatch(TestContext c){project(c,"oak",16,false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=800)
    public void spruceProjectBatch(TestContext c){project(c,"spruce",20,false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=800)
    public void birchProjectBatch(TestContext c){project(c,"birch",12,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=800)
    public void acaciaProjectBatch(TestContext c){project(c,"acacia",24,false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_trees",tickLimit=800)
    public void cherryProjectBatch(TestContext c){project(c,"cherry",16,true);}
}
