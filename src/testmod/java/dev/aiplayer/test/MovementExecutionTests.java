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
public final class MovementExecutionTests implements FabricGameTest {
    private void travel(TestContext c,int scenario){
        V070Fixtures.floor(c,24,0,Blocks.STONE);V070Fixtures.floor(c,24,1,Blocks.DIRT);
        BlockPos start=new BlockPos(4,2,6),goal=new BlockPos(12,2,6);boolean mutable=true;
        if(scenario==1)goal=new BlockPos(12,2,14);
        if(scenario==2){for(int z=0;z<13;z++)for(int y=2;y<=4;y++){BlockPos p=c.getAbsolutePos(new BlockPos(8,y,z));c.getWorld().setBlockState(p,Blocks.BRICKS.getDefaultState());OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),p);}}
        if(scenario==3 || scenario==4){for(int z=0;z<24;z++)for(int y=2;y<=6;y++)c.setBlockState(new BlockPos(8,y,z),scenario==3?Blocks.DIRT:Blocks.STONE);}
        if(scenario>=10 && scenario<=12){int gap=scenario-9;for(int x=0;x<24;x++)for(int z=0;z<24;z++)if(x<7 || x>=7+gap)c.setBlockState(new BlockPos(x,3,z),Blocks.STONE);start=new BlockPos(5,4,6);goal=new BlockPos(12,4,6);mutable=false;}
        if(scenario>=20 && scenario<=22){int gap=scenario-19;for(int x=0;x<24;x++)for(int z=0;z<24;z++){
            if(x<7 || x>=7+gap)c.setBlockState(new BlockPos(x,3,z),Blocks.STONE);c.setBlockState(new BlockPos(x,6,z),Blocks.BEDROCK);
        }start=new BlockPos(5,4,6);goal=new BlockPos(12,4,6);}
        if(scenario==30 || scenario==31){int drop=scenario-28;for(int x=0;x<=6;x++)for(int z=0;z<24;z++)for(int y=2;y<=1+drop;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.STONE);start=new BlockPos(5,2+drop,6);mutable=false;}
        if(scenario==32 || scenario==33){int rise=scenario-31;for(int x=7;x<24;x++)for(int z=0;z<24;z++)for(int y=2;y<=1+rise;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.STONE);goal=new BlockPos(12,2+rise,6);}
        if(scenario>=40 && scenario<=43){
            for(int x=5;x<11;x++)for(int z=0;z<24;z++){c.setBlockState(new BlockPos(x,1,z),Blocks.WATER);if(scenario>=41)c.setBlockState(new BlockPos(x,2,z),Blocks.WATER);}
            start=scenario==43?new BlockPos(7,2,6):new BlockPos(4,2,6);goal=new BlockPos(13,2,6);mutable=false;
        }
        if(scenario==50 || scenario==51){int height=scenario==50?4:6;for(int y=2;y<=height+1;y++){
            c.setBlockState(new BlockPos(6,y,6),Blocks.STONE);c.setBlockState(new BlockPos(5,y,6),Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,Direction.WEST));}
            start=scenario==50?new BlockPos(5,2,6):new BlockPos(5,height+1,6);goal=scenario==50?new BlockPos(5,height+1,6):new BlockPos(5,2,6);mutable=false;
        }
        if(scenario==60 || scenario==61){goal=new BlockPos(4,scenario==60?3:4,6);}
        CompanionEntity npc=V070Fixtures.worker(c,start);npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        npc.backpack.setStack(0,new ItemStack(Items.DIRT,16));BlockPos destination=c.getAbsolutePos(goal),initial=c.getAbsolutePos(start);
        final int mode=scenario;
        PreparedRoute route=new PreparedRoute(npc,p->p.getX()>=initial.getX()-3 && p.getX()<=destination.getX()+3
            && (mode==2 || mode==1 || Math.abs(p.getZ()-destination.getZ())<=1));route.allowChanges(mutable);
        boolean[] arrived={false},airborne={false};float health=npc.getHealth();
        c.runAtEveryTick(()->{
            if(npc.age<5 || arrived[0])return;
            if(!npc.isOnGround())airborne[0]=true;
            if(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<0.4 && Math.abs(npc.getY()-destination.getY())<0.25 && (npc.isOnGround() || mode>=40 && mode<=51)){arrived[0]=true;route.pause();return;}
            if(route.active())route.tick();else route.begin(destination,0);
        });
        c.runAtTick(850,()->{
            c.assertTrue(arrived[0],"Physical route failed scenario="+mode+" pos="+npc.getPos()+" "+route.diagnostics());
            c.assertTrue(npc.getHealth()>=health-3,"Route caused avoidable major damage");
            if(mode>=10 && mode<=12)c.assertTrue(airborne[0] && npc.backpack.count(Items.DIRT)==16,"Jump used fake supports");
            if(mode>=20 && mode<=22)c.assertTrue(npc.backpack.count(Items.DIRT)==16-(mode-19),"Bridge support accounting incorrect");
            if(mode==60 || mode==61)c.assertTrue(npc.backpack.count(Items.DIRT)<16,"Pillar rose without real blocks");
            c.complete();
        });
    }
    private void jumpValidation(TestContext c,int variant){
        V070Fixtures.floor(c,16,1,Blocks.STONE);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(4,2,6));
        BlockPos from=c.getAbsolutePos(new BlockPos(4,2,6)),to=from.east(3);
        for(int x=5;x<=6;x++)c.setBlockState(new BlockPos(x,1,6),Blocks.AIR);
        if(variant==0)c.getWorld().setBlockState(from.up(2),Blocks.STONE.getDefaultState());
        if(variant==1)c.getWorld().setBlockState(to,Blocks.STONE.getDefaultState());
        if(variant==2)c.getWorld().setBlockState(from.east().up(),Blocks.WATER.getDefaultState());
        if(variant==3)to=to.up(2);
        if(variant==4)to=to.down(2);
        if(variant==5)to=from.east(8);
        c.assertFalse(JumpMotion.possible(npc,from,to,p->variant!=6 || p.getX()<from.getX()+2),"Unsafe jump accepted: "+variant);c.complete();
    }
    private void blocked(TestContext c,int variant){
        V070Fixtures.floor(c,16,1,Blocks.STONE);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(4,2,6));
        BlockPos wall=c.getAbsolutePos(new BlockPos(7,2,6));
        for(int z=0;z<16;z++)for(int y=2;y<=7;y++){
            BlockPos p=c.getAbsolutePos(new BlockPos(7,y,z));c.getWorld().setBlockState(p,(variant==2?Blocks.OBSIDIAN:Blocks.DIRT).getDefaultState());
            if(variant==0)OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),p);
            if(variant==1)OwnedBlocks.get(c.getWorld()).claim(p,UUID.randomUUID());
        }
        if(variant==3)c.getWorld().setBlockState(wall.west(),Blocks.LAVA.getDefaultState());
        PreparedRoute route=new PreparedRoute(npc,p->Math.abs(p.getZ()-wall.getZ())<2 && p.getY()<=wall.getY()+3);route.allowChanges(variant!=4);
        c.runAtEveryTick(()->{if(npc.age>4)route.begin(wall.east(3),0);});
        c.runAtTick(90,()->{c.assertFalse(c.getWorld().getBlockState(wall).isAir(),"Unsafe route cut wall: "+variant);c.assertTrue(npc.getX()<wall.getX(),"Unsafe route crossed obstacle");c.complete();});
    }
    private void cancellation(TestContext c,int variant){
        V070Fixtures.floor(c,20,1,Blocks.STONE);CompanionEntity npc=V070Fixtures.worker(c,new BlockPos(3,2,6));
        BlockPos goal=c.getAbsolutePos(new BlockPos(16,2,6));PreparedRoute route=new PreparedRoute(npc,p->true);
        c.runAtTick(5,()->route.begin(goal,0));
        c.runAtTick(10,()->{
            if(variant==0)npc.setMode(Mode.WAIT);
            if(variant==1)npc.navigationPlan().cancel();
            if(variant==2){NbtCompound n=new NbtCompound();npc.navigationPlan().write(n);npc.navigationPlan().read(n);}
            if(variant==3)npc.getNavigation().startMovingAlong(WorkNavigation.findPath(npc,npc.getBlockPos().south(2),0),1);
            c.assertFalse(route.active(),"Previous executor still owns movement after cancellation/retarget");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void straightPhysicalWalking(TestContext c){travel(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void diagonalOpenGround(TestContext c){travel(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void routeAroundProtectedWall(TestContext c){travel(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void digNaturalDirtPassage(TestContext c){travel(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void digStoneWithRealPick(TestContext c){travel(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void jumpOneBlockGap(TestContext c){travel(c,10);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void jumpTwoBlockGap(TestContext c){travel(c,11);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void sprintJumpThreeBlockGap(TestContext c){travel(c,12);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void bridgeOneBlockWithLowCeiling(TestContext c){travel(c,20);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void bridgeTwoBlocksWithLowCeiling(TestContext c){travel(c,21);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void bridgeThreeBlocksWithLowCeiling(TestContext c){travel(c,22);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void controlledTwoBlockDrop(TestContext c){travel(c,30);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void controlledThreeBlockDrop(TestContext c){travel(c,31);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void oneBlockAscent(TestContext c){travel(c,32);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void twoBlockRiseUsesPaidSupport(TestContext c){travel(c,33);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void shallowWaterCrossing(TestContext c){travel(c,40);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void deepPoolCrossing(TestContext c){travel(c,41);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void poolToDryBank(TestContext c){travel(c,42);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void workerStartsInPool(TestContext c){travel(c,43);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void ladderAscent(TestContext c){travel(c,50);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void ladderDescent(TestContext c){travel(c,51);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void singlePaidPillar(TestContext c){travel(c,60);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=920)
    public void twoPaidPillars(TestContext c){travel(c,61);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsLowCeiling(TestContext c){jumpValidation(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsOccupiedLanding(TestContext c){jumpValidation(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsWaterInArc(TestContext c){jumpValidation(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsTwoBlockRise(TestContext c){jumpValidation(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsUnboundedFall(TestContext c){jumpValidation(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRejectsExcessiveSpan(TestContext c){jumpValidation(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void jumpRespectsPermissionBoundary(TestContext c){jumpValidation(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=130)
    public void protectedWallPreserved(TestContext c){blocked(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=130)
    public void foreignSupportPreserved(TestContext c){blocked(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=130)
    public void requiredPickNotInvented(TestContext c){blocked(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=130)
    public void lavaPocketNotOpened(TestContext c){blocked(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=130)
    public void readOnlyRouteDoesNotDig(TestContext c){blocked(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void modeChangeCancelsMovement(TestContext c){cancellation(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void explicitCancelReleasesExecutor(TestContext c){cancellation(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void reloadDiscardsStalePath(TestContext c){cancellation(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void ordinaryRetargetReplacesPreparedPath(TestContext c){cancellation(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void zeroLengthRouteDoesNotSpendBlocks(TestContext c){V070Fixtures.floor(c,12,1,Blocks.STONE);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.backpack.setStack(0,new ItemStack(Items.DIRT,3));c.runAtTick(5,()->{var r=new PreparedRoute(n,p->true);r.begin(n.getBlockPos(),0);c.assertTrue(!r.active() && n.backpack.count(Items.DIRT)==3,"Zero route mutated terrain");c.complete();});}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=260)
    public void failedCellsExpire(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(4,2,4));BlockPos p=n.getBlockPos().east();n.navigationPlan().failed(p);c.assertTrue(n.navigationPlan().avoid(p),"Failed cell not remembered");c.runAtTick(220,()->{c.assertFalse(n.navigationPlan().avoid(p),"Failure never expires after terrain can change");c.complete();});}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void navigationSaveCarriesDimension(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(4,2,4));NbtCompound data=new NbtCompound();n.navigationPlan().write(data);c.assertTrue(data.getCompound("Navigation").getString("Dimension").equals(c.getWorld().getRegistryKey().getValue().toString()),"Saved goal missing dimension");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void oldSaveWithoutNavigationStartsIdle(TestContext c){var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.navigationPlan().read(new NbtCompound());c.assertTrue(n.navigationPlan().outcome()==NpcNavigation.Outcome.IDLE,"Legacy save starts with stale path");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void worldChangeInvalidatesJumpArc(TestContext c){V070Fixtures.floor(c,16,1,Blocks.STONE);var n=V070Fixtures.worker(c,new BlockPos(4,2,6));BlockPos from=n.getBlockPos(),to=from.east(3);c.getWorld().setBlockState(from.east().down(),Blocks.AIR.getDefaultState());c.getWorld().setBlockState(from.east(2).down(),Blocks.AIR.getDefaultState());c.assertTrue(JumpMotion.possible(n,from,to,p->true),"Initial arc should be open");c.getWorld().setBlockState(from.east().up(2),Blocks.STONE.getDefaultState());c.assertFalse(JumpMotion.possible(n,from,to,p->true),"Cached jump ignored new ceiling");c.complete();}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_movement",tickLimit=100)
    public void preparedMovementOwnsChunkTicket(TestContext c){V070Fixtures.floor(c,20,1,Blocks.STONE);var n=V070Fixtures.worker(c,new BlockPos(3,2,5));n.tasks().startAt(CompanionTasks.Kind.TEND_FARM,n.getBlockPos(),null);c.runAtTick(30,()->{c.assertTrue(CompanionChunkLoading.loadedCenter(n)!=null,"Work route has no ticking chunk");n.tasks().cancel();c.complete();});}
}
