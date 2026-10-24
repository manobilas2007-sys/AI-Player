package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.navigation.NpcPathfinder;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Runs on the GameTest server without a Baritone mod or a client player. */
public final class BaritoneNavigationTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c,BlockPos start){
        for(int x=0;x<=12;x++)for(int z=0;z<=12;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity a=c.spawnEntity(AIPlayer.COMPANION,start);
        a.autoCraft=false;a.autoSurvive=false;a.autoLoot=false;
        return a;
    }
    private BlockPos absolute(TestContext c,int x,int z){return c.getAbsolutePos(new BlockPos(x,2,z));}
    private void highBlock(TestContext c,int x,int z){
        for(int y=2;y<=4;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.STONE);
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation",tickLimit=280)
    public void embeddedPlannerPhysicallyWalksAroundProtectedWall(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        for(int z=0;z<=7;z++)for(int y=2;y<=4;y++){
            BlockPos p=new BlockPos(5,y,z);c.setBlockState(p,Blocks.STONE);
            OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),c.getAbsolutePos(p));
        }
        BlockPos destination=absolute(c,9,2);
        c.runAtTick(5,()->{
            Path path=WorkNavigation.findPath(a,destination,0);
            c.assertTrue(path!=null && path.reachesTarget(),"No complete detour: "+NpcPathfinder.diagnostics(a));
            c.assertTrue(a.getNavigation().startMovingAlong(path,1.0),"NPC rejected embedded route");
        });
        c.runAtTick(20,()->c.assertTrue(a.squaredDistanceTo(Vec3d.ofBottomCenter(destination))>1,
            "Route teleported the NPC to its goal"));
        c.runAtTick(250,()->{
            c.assertTrue(a.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<2,
                "NPC did not execute the detour: "+a.getPos()+" "+NpcPathfinder.diagnostics(a));
            for(int z=0;z<=7;z++)for(int y=2;y<=4;y++)c.assertTrue(
                c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(5,y,z))).isOf(Blocks.STONE),
                "Planner destroyed the protected wall");
            c.assertTrue(a.getMainHandStack().getDamage()==0 && a.backpack.isEmpty(),
                "A read-only movement plan consumed tools or fabricated resources");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void revalidatesCachedRoutesWhenTerrainChanges(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));BlockPos destination=absolute(c,9,2);
        c.assertTrue(WorkNavigation.findPath(a,destination,0)!=null,"Initial route missing");
        c.assertTrue(WorkNavigation.findPath(a,destination,0)!=null && NpcPathfinder.stats(a).cacheHit(),
            "An unchanged route was not reusable");
        highBlock(c,5,2);
        Path replanned=WorkNavigation.findPath(a,destination,0);
        c.assertTrue(replanned!=null && replanned.reachesTarget() && !NpcPathfinder.stats(a).cacheHit(),
            "Changed collision reused an obsolete route");
        for(int i=0;i<replanned.getLength();i++)c.assertFalse(
            new BlockPos(replanned.getNode(i).x,replanned.getNode(i).y,replanned.getNode(i).z).equals(absolute(c,5,2)),
            "Route still crosses the new wall");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void diagonalSearchDoesNotCutBlockedCorners(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));highBlock(c,3,2);highBlock(c,2,3);
        BlockPos destination=absolute(c,3,3);Path path=WorkNavigation.findPath(a,destination,0);
        c.assertTrue(path!=null && path.getLength()>2,"Diagonal shortcut crossed colliding corner blocks");
        c.assertFalse(path.getNode(1).x==destination.getX() && path.getNode(1).z==destination.getZ(),
            "Planner cut directly through a blocked corner");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void waterPolicyRequiresAirAndAvoidsLava(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));
        c.setBlockState(new BlockPos(3,2,2),Blocks.WATER);c.setBlockState(new BlockPos(4,2,2),Blocks.WATER);
        BlockPos water=absolute(c,4,2);
        c.assertTrue(WorkNavigation.findPath(a,water,0,NpcPathfinder.Mode.WATER)!=null,"Safe water goal was refused");
        c.assertTrue(WorkNavigation.findPath(a,water,0,NpcPathfinder.Mode.WORK)==null,"Dry work route entered water");
        a.setAir(100);
        c.assertTrue(WorkNavigation.findPath(a,water,0,NpcPathfinder.Mode.WATER)==null,"Low-air swimmer reused a water route");
        a.setAir(300);c.setBlockState(new BlockPos(5,2,2),Blocks.LAVA);
        Path path=WorkNavigation.findPath(a,absolute(c,9,2),0);
        c.assertTrue(path!=null,"No safe route around the fluid pocket");
        for(int i=0;i<path.getLength();i++){
            var node=path.getNode(i);BlockPos p=new BlockPos(node.x,node.y,node.z);
            c.assertTrue(c.getWorld().getFluidState(p).isEmpty(),"Dry route crossed fluid");
        }
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void partialOrOutOfPolicyPlansAreNeverExecuted(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));BlockPos destination=absolute(c,9,2);
        Path path=WorkNavigation.findPath(a,destination,0,NpcPathfinder.Mode.WORK,
            p->p.getX()<=absolute(c,5,2).getX());
        c.assertTrue(path==null,"Partial policy-limited route was exposed as complete");
        c.assertFalse(NpcPathfinder.stats(a).status().equals("COMPLETE"),"Incomplete goal reported success");
        c.assertTrue(a.getNavigation().isIdle() && a.backpack.isEmpty(),"Search changed NPC execution state");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void searchesNeverGenerateUnknownChunks(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));
        BlockPos far=new BlockPos(3000000,a.getBlockY(),3000000);
        c.assertFalse(c.getWorld().isChunkLoaded(far),"Fixture unexpectedly loaded a remote chunk");
        c.assertTrue(WorkNavigation.findPath(a,far,0)==null && NpcPathfinder.stats(a).expandedNodes()==0,
            "Out-of-range search expanded an unknown world");
        c.assertFalse(c.getWorld().isChunkLoaded(far),"Search loaded/generated an unknown chunk");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation")
    public void cachesAreIsolatedBetweenNpcsAndStartPositions(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));
        CompanionEntity b=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(2,2,8));
        BlockPos first=absolute(c,9,2),second=absolute(c,9,8);
        Path pa=WorkNavigation.findPath(a,first,0),pb=WorkNavigation.findPath(b,second,0);
        c.assertTrue(pa!=null && pb!=null && pa!=pb,"NPCs shared a mutable path");
        c.assertTrue(WorkNavigation.findPath(a,first,0)!=null && NpcPathfinder.stats(a).cacheHit(),
            "The second NPC overwrote the first NPC's route");
        Vec3d moved=Vec3d.ofBottomCenter(absolute(c,3,2));
        a.refreshPositionAndAngles(moved.x,moved.y,moved.z,0,0);
        c.assertTrue(WorkNavigation.findPath(a,first,0)!=null && !NpcPathfinder.stats(a).cacheHit(),
            "Route retained an obsolete actor start position");
        c.assertTrue(WorkNavigation.findPath(b,second,0)!=null && NpcPathfinder.stats(b).cacheHit(),
            "Moving one NPC invalidated another NPC's route");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation",tickLimit=20)
    public void manyUnreachableRequestsShareOneTickBudget(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,2));BlockPos destination=absolute(c,9,2);
        int barrier=absolute(c,5,2).getX();
        for(int attempt=0;attempt<256 && !NpcPathfinder.stats(a).status().equals("BUDGET");attempt++)
            c.assertTrue(WorkNavigation.findPath(a,destination,0,NpcPathfinder.Mode.WORK,
                p->p.getX()!=barrier)==null,"Policy barrier unexpectedly crossed");
        c.assertTrue(NpcPathfinder.stats(a).status().equals("BUDGET") && NpcPathfinder.budgetUsed(a)<=8192,
            "Repeated callers exceeded the per-NPC tick budget");
        c.runAtTick(3,()->c.assertTrue(WorkNavigation.findPath(a,destination,0)!=null,
            "Search budget did not reset on the next world tick"));
        c.runAtTick(4,c::complete);
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="baritone_navigation",tickLimit=180)
    public void walksAcrossFarmlandAndDirtPathWithoutJumping(TestContext c){
        CompanionEntity a=worker(c,new BlockPos(2,2,3));
        for(int x=2;x<=6;x++)c.setBlockState(new BlockPos(x,1,3),Blocks.FARMLAND);
        for(int x=7;x<=9;x++)c.setBlockState(new BlockPos(x,1,3),Blocks.DIRT_PATH);
        BlockPos origin=absolute(c,2,3),destination=absolute(c,9,3);
        a.refreshPositionAndAngles(origin.getX()+0.5,origin.getY()-0.0625,origin.getZ()+0.5,0,0);
        c.runAtTick(5,()->{
            Path path=WorkNavigation.findPath(a,destination,0);
            c.assertTrue(path!=null && path.reachesTarget(),"Fractional farm soil blocked the route");
            for(int i=0;i<path.getLength();i++)c.assertTrue(path.getNode(i).y==destination.getY(),
                "Farm soil introduced a spurious jump/drop node");
            c.assertTrue(a.getNavigation().startMovingAlong(path,1.0),"NPC could not leave farmland origin");
        });
        for(int tick=10;tick<150;tick+=10)c.runAtTick(tick,()->c.assertTrue(
            a.getY()<=origin.getY()+0.1 && a.getY()>=origin.getY()-0.2,
            "NPC jumped or fell while crossing shallow farm soil: "+a.getPos()));
        c.runAtTick(150,()->{
            c.assertTrue(a.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<2,
                "NPC failed to cross farmland and dirt path: "+a.getPos());
            c.assertTrue(a.fallDistance==0,"Walking on farm soil accumulated a fall");
            c.complete();
        });
    }
}
