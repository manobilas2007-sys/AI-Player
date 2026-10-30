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
public final class ResourceMemoryJourneyTests implements FabricGameTest {
    private void scan(TestContext c,String block,Item item,boolean hidden){
        V070Fixtures.floor(c,24,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos p=c.getAbsolutePos(new BlockPos(11,2,7));
        if(hidden)for(BlockPos q:BlockPos.iterate(p.add(-1,-1,-1),p.add(1,1,1)))c.getWorld().setBlockState(q,Blocks.STONE.getDefaultState());
        c.getWorld().setBlockState(p,V070Fixtures.block(block).getDefaultState());
        c.runAtEveryTick(()->{n.resourceIndex().scan(item);c.assertTrue(n.resourceIndex().scannedThisTick()<=1024,"Scanner exceeded shared block budget");});
        c.runAtTick(100,()->{c.assertTrue(n.resourceIndex().candidates(item,null,q->q.equals(p)).contains(p),"Resource not indexed through occlusion: "+block);c.assertTrue(n.backpack.isEmpty(),"Scan mined remotely");c.complete();});
    }
    private void memory(TestContext c,int variant){
        var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos p=c.getAbsolutePos(new BlockPos(10,2,10));
        c.getWorld().setBlockState(p,Blocks.IRON_ORE.getDefaultState());n.resourceIndex().remember(Items.RAW_IRON,p);n.resourceIndex().visit(p);
        NbtCompound data=new NbtCompound();n.resourceIndex().write(data);
        if(variant==0){n.resourceIndex().read(data);c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==1,"Coordinate lost on reload");}
        if(variant==1){data.getCompound("ResourceIndex").putString("Dimension","minecraft:the_nether");n.resourceIndex().read(data);c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==0,"Different dimension reused coordinates");}
        if(variant==2){n.resourceIndex().read(new NbtCompound());c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==0,"Legacy save retained stale resources");}
        if(variant==3){data.getCompound("ResourceIndex").putInt("Version",99);n.resourceIndex().read(data);c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==0,"Unknown schema accepted");}
        if(variant==4){n.resourceIndex().read(data);c.assertTrue(n.resourceIndex().visited(p),"Explored frontier lost on reload");}
        if(variant==5){c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());c.assertFalse(n.resourceIndex().candidates(Items.RAW_IRON,null,q->true).contains(p),"Removed source stayed valid");}
        if(variant==6){n.resourceIndex().forget(Items.RAW_IRON,p);c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==0,"Failed source not invalidated");}
        if(variant==7){n.resourceIndex().remember(Items.RAW_IRON,p);c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==1,"Duplicate source grew cache");}
        c.complete();
    }
    private void journey(TestContext c,int scenario){
        BlockPos local=new BlockPos(4096+scenario*512,8,2048);BlockPos origin=c.getAbsolutePos(local);int length=80+scenario*20;
        for(int x=-3;x<=length+3;x++)for(int z=-3;z<=3;z++){
            for(int y=-2;y<=5;y++)c.getWorld().setBlockState(origin.add(x,y,z),y<0?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState());
        }
        if(scenario==1)for(int y=0;y<3;y++)for(int z=-3;z<=3;z++)c.getWorld().setBlockState(origin.add(48,y,z),Blocks.DIRT.getDefaultState());
        if(scenario==2)for(int x=40;x<48;x++)for(int z=-3;z<=3;z++)c.getWorld().setBlockState(origin.add(x,-1,z),Blocks.WATER.getDefaultState());
        if(scenario==3)for(int x=48;x<54;x++)for(int z=-3;z<=3;z++)c.getWorld().setBlockState(origin.add(x,0,z),Blocks.DIRT.getDefaultState());
        var n=V070Fixtures.worker(c,local);n.tasks().startAt(CompanionTasks.Kind.TEND_FARM,origin,null);n.backpack.setStack(0,new ItemStack(Items.DIRT,16));
        n.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));var work=new ProjectWork(n);BlockPos goal=origin.east(length);
        boolean[] outbound={false},restored={false};Set<Long> centers=new HashSet<>();
        c.runAtEveryTick(()->{
            if(n.age<5)return;
            ChunkPos center=CompanionChunkLoading.loadedCenter(n);if(center!=null)centers.add(center.toLong());
            if(scenario==4 && !restored[0] && n.getX()>origin.getX()+40){NbtCompound state=new NbtCompound();n.writeCustomDataToNbt(state);n.readCustomDataFromNbt(state);restored[0]=true;}
            if(work.moveTo(outbound[0]?origin:goal)){
                if(!outbound[0]){outbound[0]=true;n.resourceIndex().visit(goal);}
                else{c.assertTrue(centers.size()>=4,"Long journey did not move its own tickets");c.assertTrue(n.resourceIndex().visited(goal),"Return forgot explored destination");n.tasks().cancel();n.discard();c.complete();}
            }
        });
        c.runAtTick(4800,()->{c.assertTrue(false,"Long physical journey stalled: "+work.diagnostics());n.discard();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void iron_oreIndexedThroughCover(TestContext c){scan(c,"iron_ore",Items.RAW_IRON,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void deepslate_iron_oreIndexedThroughCover(TestContext c){scan(c,"deepslate_iron_ore",Items.RAW_IRON,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void coal_oreIndexedThroughCover(TestContext c){scan(c,"coal_ore",Items.COAL,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void deepslate_coal_oreIndexedThroughCover(TestContext c){scan(c,"deepslate_coal_ore",Items.COAL,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void sandIndexedThroughCover(TestContext c){scan(c,"sand",Items.SAND,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void red_sandIndexedThroughCover(TestContext c){scan(c,"red_sand",Items.RED_SAND,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void spruce_logIndexedThroughCover(TestContext c){scan(c,"spruce_log",Items.SPRUCE_LOG,true);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=160)
    public void oak_logIndexedAtSurface(TestContext c){scan(c,"oak_log",Items.OAK_LOG,false);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void resourceMemoryReload(TestContext c){memory(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void resourceMemoryDimensionIsolation(TestContext c){memory(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void oldSaveHasEmptyIndex(TestContext c){memory(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void unknownIndexVersionDiscarded(TestContext c){memory(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void frontierMemoryReload(TestContext c){memory(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void removedResourceRevalidated(TestContext c){memory(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void failedResourceForgotten(TestContext c){memory(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void coordinatesDeduplicated(TestContext c){memory(c,7);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void memoryStorageBounded(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(4,2,4));for(int i=0;i<700;i++)n.resourceIndex().remember(Items.RAW_IRON,n.getBlockPos().east(i));c.assertTrue(n.resourceIndex().remembered(Items.RAW_IRON)==512,"Unbounded resource memory");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void frontierStorageBounded(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(4,2,4));for(int i=0;i<2100;i++)n.resourceIndex().visit(new BlockPos(i*16,64,0));NbtCompound data=new NbtCompound();n.resourceIndex().write(data);c.assertTrue(data.getCompound("ResourceIndex").getLongArray("Visited").length==2048,"Unbounded exploration memory");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void allRequestsShareScanningBudget(TestContext c){V070Fixtures.floor(c,24,1,Blocks.IRON_ORE);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));for(int i=0;i<20;i++)n.resourceIndex().scan(i%2==0?Items.RAW_IRON:Items.DIRT);c.assertTrue(n.resourceIndex().scannedThisTick()<=1024,"Per-item calls bypassed scan budget");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=100)
    public void returnTripAffectsResourceRanking(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(8,2,8));BlockPos a=n.getBlockPos().east(10),b=n.getBlockPos().west(10);c.getWorld().setBlockState(a,Blocks.IRON_ORE.getDefaultState());c.getWorld().setBlockState(b,Blocks.IRON_ORE.getDefaultState());n.resourceIndex().remember(Items.RAW_IRON,a);n.resourceIndex().remember(Items.RAW_IRON,b);var ranked=n.resourceIndex().candidates(Items.RAW_IRON,b.west(20),p->p.equals(a)||p.equals(b));c.assertTrue(ranked.get(0).equals(b),"Planner ignored material return journey");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=5000)
    public void eightyBlockRoundTrip(TestContext c){journey(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=5000)
    public void hundredBlockTripWithDigging(TestContext c){journey(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=5000)
    public void longTripAcrossWater(TestContext c){journey(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=5000)
    public void longTripOverRaisedTerrain(TestContext c){journey(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_memory",tickLimit=5000)
    public void longTripReloadAndReturn(TestContext c){journey(c,4);}
}
