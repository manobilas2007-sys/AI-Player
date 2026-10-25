package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.navigation.NpcPathfinder;
import dev.aiplayer.ai.navigation.OwnedDoorNavigation;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Real door blocks and entity movement, with no permission to open a foreign
 * building. Planning itself must never change the world's door state. */
public final class OwnedDoorNavigationTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<13;x++)for(int z=0;z<10;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(3,2,4));
        npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=false;
        return npc;
    }
    private BlockPos door(TestContext c,CompanionEntity npc,Block block,boolean own){
        BlockPos at=c.getAbsolutePos(new BlockPos(6,2,4));
        BlockState lower=block.getDefaultState().with(DoorBlock.FACING,Direction.EAST)
            .with(DoorBlock.HALF,DoubleBlockHalf.LOWER).with(DoorBlock.OPEN,false);
        c.getWorld().setBlockState(at,lower,2);
        c.getWorld().setBlockState(at.up(),lower.with(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        c.getWorld().updateNeighbors(at,block);c.getWorld().updateNeighbors(at.up(),block);
        if(own){OwnedBlocks.get(c.getWorld()).claim(at,npc.getUuid());OwnedBlocks.get(c.getWorld()).claim(at.up(),npc.getUuid());}
        else{OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),at);OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),at.up());}
        return at;
    }
    private Path corridor(TestContext c,CompanionEntity npc){
        BlockPos from=c.getAbsolutePos(new BlockPos(2,2,4)),end=c.getAbsolutePos(new BlockPos(9,2,4));
        return NpcPathfinder.findPath(npc,end,0,NpcPathfinder.Mode.WORK,
            p->p.getY()==from.getY() && p.getZ()==from.getZ() && p.getX()>=from.getX() && p.getX()<=end.getX());
    }
    private void ticks(TestContext c,CompanionEntity npc,int until){
        for(int tick=6;tick<=until;tick++)c.runAtTick(tick,()->OwnedDoorNavigation.tick(npc));
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="owned_doors",tickLimit=180)
    public void ownedClosedDoorHasReadOnlyPlanThenPhysicalPassageAndClosure(TestContext c){
        CompanionEntity npc=worker(c);BlockPos door=door(c,npc,Blocks.SPRUCE_DOOR,true);
        BlockPos destination=c.getAbsolutePos(new BlockPos(9,2,4));
        c.runAtTick(5,()->{
            Path path=corridor(c,npc);
            c.assertTrue(path!=null && path.reachesTarget(),"Owned closed door prevented the complete route");
            c.assertFalse(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"Planning opened the door remotely");
            c.assertTrue(npc.getNavigation().startMovingAlong(path,1.0),"NPC refused door route");
        });
        ticks(c,npc,150);
        c.runAtTick(9,()->c.assertTrue(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"Nearby routed NPC never opened its own door"));
        c.runAtTick(155,()->{
            c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<1.5,
                "NPC did not physically pass the doorway: "+npc.getPos());
            c.assertFalse(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"NPC left its operated door open after passing");
            c.assertTrue(OwnedBlocks.get(c.getWorld()).owned(door,npc.getUuid())
                && OwnedBlocks.get(c.getWorld()).owned(door.up(),npc.getUuid()),"Door interaction lost provenance");
            c.assertTrue(npc.backpack.isEmpty(),"Walking through a door fabricated inventory items");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="owned_doors",tickLimit=60)
    public void foreignWoodenAndOwnedIronDoorsStayClosedAndBlockRoutes(TestContext c){
        CompanionEntity npc=worker(c);BlockPos door=door(c,npc,Blocks.IRON_DOOR,true);
        c.assertTrue(corridor(c,npc)==null,"NPC planned to hand-open an iron door");
        c.assertTrue(OwnedDoorNavigation.ownedDoor(npc,c.getWorld(),door)==null,"Iron door was classified as hand-openable");
        door(c,npc,Blocks.OAK_DOOR,false);
        c.assertTrue(corridor(c,npc)==null,"NPC planned through the player's closed door");
        OwnedDoorNavigation.tick(npc);
        c.assertFalse(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"NPC opened a protected player door");
        c.assertTrue(OwnedBlocks.get(c.getWorld()).protectedBlock(door),"Player door lost protection");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="owned_doors",tickLimit=190)
    public void operatedDoorWaitsForAnotherLivingActorBeforeClosing(TestContext c){
        CompanionEntity npc=worker(c);BlockPos door=door(c,npc,Blocks.OAK_DOOR,true);
        ArmorStandEntity[] actor={null};
        c.runAtTick(5,()->{
            Path path=corridor(c,npc);c.assertTrue(path!=null,"No route for closure fixture");
            c.assertTrue(npc.getNavigation().startMovingAlong(path,1.0),"NPC rejected closure fixture route");
        });
        ticks(c,npc,175);
        c.runAtTick(9,()->{
            c.assertTrue(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"Fixture never opened door");
            // A marker is a real living entity with no physical obstruction,
            // allowing the NPC to pass while testing the doorway occupancy.
            actor[0]=c.spawnEntity(EntityType.ARMOR_STAND,new BlockPos(6,2,4));
            NbtCompound data=new NbtCompound();data.putBoolean("Marker",true);actor[0].readCustomDataFromNbt(data);
            actor[0].setNoGravity(true);
        });
        c.runAtTick(120,()->{
            c.assertTrue(npc.getX()>door.getX()+1,"NPC never passed the occupied doorway fixture");
            c.assertTrue(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"Door closed on a living actor");
            actor[0].discard();
        });
        c.runAtTick(180,()->{
            c.assertFalse(c.getWorld().getBlockState(door).get(DoorBlock.OPEN),"Empty doorway never closed after actor left");c.complete();
        });
    }
}
