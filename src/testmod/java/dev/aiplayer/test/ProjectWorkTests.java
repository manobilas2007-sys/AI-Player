package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

/** Integration coverage for physical construction work, separate from plans. */
public final class ProjectWorkTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c,BlockPos feet){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,feet);
        // An unowned worker has vanilla movement but no competing brain order;
        // the test calls the same executor the project coordinator delegates to.
        npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=false;
        return npc;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=220)
    public void physicallyClearsSnowAndGrassBeforeConsumingRealPlanks(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,4));ProjectWork work=new ProjectWork(npc);
        BlockPos snow=c.getAbsolutePos(new BlockPos(5,2,4)),grass=c.getAbsolutePos(new BlockPos(5,2,6));
        c.getWorld().setBlockState(snow,Blocks.SNOW.getDefaultState());
        c.getWorld().setBlockState(grass,Blocks.GRASS.getDefaultState());
        npc.backpack.setStack(0,new ItemStack(Items.SPRUCE_PLANKS,2));
        boolean[] placed={false,false};
        c.runAtEveryTick(()->{
            if(!placed[0])placed[0]=work.place(snow,Blocks.SPRUCE_PLANKS.getDefaultState(),Items.SPRUCE_PLANKS,1);
            else if(!placed[1])placed[1]=work.place(grass,Blocks.SPRUCE_PLANKS.getDefaultState(),Items.SPRUCE_PLANKS,1);
        });
        c.runAtTick(180,()->{
            c.assertTrue(placed[0] && placed[1],"Snow/grass preparation did not complete: "+work.status());
            c.assertTrue(c.getWorld().getBlockState(snow).isOf(Blocks.SPRUCE_PLANKS)
                && c.getWorld().getBlockState(grass).isOf(Blocks.SPRUCE_PLANKS),"Prepared positions have the wrong palette");
            c.assertTrue(npc.backpack.count(Items.SPRUCE_PLANKS)==0,"Placement did not consume exactly two real planks");
            c.assertTrue(c.getWorld().getBlockState(snow.down()).isOf(Blocks.DIRT),"Preparation removed the supporting ground");
            c.assertTrue(OwnedBlocks.get(c.getWorld()).owned(snow,npc.getUuid())
                && OwnedBlocks.get(c.getWorld()).owned(grass,npc.getUuid()),"Placed project blocks lost provenance");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=100)
    public void refusesPlayerVegetationAndAnotherCompanionsTerrain(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,4));ProjectWork work=new ProjectWork(npc);
        BlockPos player=c.getAbsolutePos(new BlockPos(5,2,4)),foreign=c.getAbsolutePos(new BlockPos(5,2,6));
        c.getWorld().setBlockState(player,Blocks.GRASS.getDefaultState());
        c.getWorld().setBlockState(foreign,Blocks.DIRT.getDefaultState());
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),player);
        OwnedBlocks.get(c.getWorld()).claim(foreign,UUID.randomUUID());
        npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS));
        c.runAtEveryTick(()->{
            work.place(player,Blocks.OAK_PLANKS.getDefaultState(),Items.OAK_PLANKS,1);work.safeMine(foreign);
        });
        c.runAtTick(70,()->{
            c.assertTrue(c.getWorld().getBlockState(player).isOf(Blocks.GRASS),"Builder erased player-protected vegetation");
            c.assertTrue(c.getWorld().getBlockState(foreign).isOf(Blocks.DIRT),"Builder erased another NPC's terrain");
            c.assertTrue(npc.backpack.count(Items.OAK_PLANKS)==1,"Blocked work consumed a building material");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=140)
    public void placesTwoDoorHalvesUsingOneItemAndRemainsIdempotent(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,4));ProjectWork work=new ProjectWork(npc);
        BlockPos door=c.getAbsolutePos(new BlockPos(5,2,4));npc.backpack.setStack(0,new ItemStack(Items.SPRUCE_DOOR,2));
        var state=Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING,Direction.NORTH);
        c.runAtEveryTick(()->work.placeDoor(door,state,Items.SPRUCE_DOOR));
        c.runAtTick(100,()->{
            var low=c.getWorld().getBlockState(door);var high=c.getWorld().getBlockState(door.up());
            c.assertTrue(low.isOf(Blocks.SPRUCE_DOOR) && high.isOf(Blocks.SPRUCE_DOOR),"Real door did not occupy both cells: "+work.status());
            c.assertTrue(low.get(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && high.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Door half properties are invalid");
            c.assertTrue(npc.backpack.count(Items.SPRUCE_DOOR)==1,"Door consumed zero/two items or duplicated on retry");
            c.assertTrue(OwnedBlocks.get(c.getWorld()).owned(door,npc.getUuid())
                && OwnedBlocks.get(c.getWorld()).owned(door.up(),npc.getUuid()),"Door halves were not claimed together");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=600)
    public void repeatedRequestsWalkAroundWallsAndOnlyReportPhysicalArrival(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(1,2,5));ProjectWork work=new ProjectWork(npc);
        BlockPos destination=c.getAbsolutePos(new BlockPos(30,2,5));Vec3d initial=npc.getPos();
        for(int z=0;z<=12;z++)for(int y=2;y<=4;y++){
            BlockPos wall=c.getAbsolutePos(new BlockPos(15,y,z));c.getWorld().setBlockState(wall,Blocks.STONE.getDefaultState());
            OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),wall);
        }
        boolean[] arrived={false};
        c.runAtEveryTick(()->{
            if(!arrived[0]){
                arrived[0]=work.moveTo(destination);
                if(arrived[0])c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<0.65,
                    "Intermediate route planning was reported as arrival");
            }
        });
        c.runAtTick(520,()->{
            c.assertTrue(arrived[0] && npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<2,
                "Repeated orders reset movement instead of reaching the destination: "+work.status()+" "+npc.getPos());
            c.assertTrue(npc.getPos().squaredDistanceTo(initial)>20*20,"NPC never crossed the arena physically");
            for(int z=0;z<=12;z++)for(int y=2;y<=4;y++)c.assertTrue(
                c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(15,y,z))).isOf(Blocks.STONE),"Movement destroyed a protected wall");
            work.setBoundary(destination,8);Vec3d before=npc.getPos();
            c.assertFalse(work.moveTo(destination.add(9,0,0)),"Movement accepted a destination outside its authorized radius");
            c.assertTrue(before.equals(npc.getPos()),"Boundary rejection changed the actor's position");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=90)
    public void existingOwnedBlocksMustMatchStairDirectionAndSlabHalf(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,4));ProjectWork work=new ProjectWork(npc);
        BlockPos stair=c.getAbsolutePos(new BlockPos(5,2,4)),slab=c.getAbsolutePos(new BlockPos(5,2,6));
        var wrongStair=Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.WEST);
        var intendedStair=wrongStair.with(StairsBlock.FACING,Direction.EAST);
        var wrongSlab=Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM);
        var intendedSlab=wrongSlab.with(SlabBlock.TYPE,SlabType.TOP);
        c.getWorld().setBlockState(stair,wrongStair);c.getWorld().setBlockState(slab,wrongSlab);
        OwnedBlocks.get(c.getWorld()).claim(stair,npc.getUuid());OwnedBlocks.get(c.getWorld()).claim(slab,npc.getUuid());
        npc.backpack.setStack(0,new ItemStack(Items.SPRUCE_STAIRS));npc.backpack.setStack(1,new ItemStack(Items.SPRUCE_SLAB));
        c.runAtTick(20,()->{
            c.assertFalse(work.place(stair,intendedStair,Items.SPRUCE_STAIRS,1),"Wrong stair direction counted as completed construction");
            c.assertFalse(work.place(slab,intendedSlab,Items.SPRUCE_SLAB,1),"Wrong slab half counted as completed construction");
            c.assertTrue(c.getWorld().getBlockState(stair).get(StairsBlock.FACING)==Direction.WEST
                && c.getWorld().getBlockState(slab).get(SlabBlock.TYPE)==SlabType.BOTTOM,"Mismatch silently rotated/replaced an existing block");
            c.assertTrue(npc.backpack.count(Items.SPRUCE_STAIRS)==1 && npc.backpack.count(Items.SPRUCE_SLAB)==1,
                "Rejected reconciliation consumed building materials");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=680)
    public void walksBeyondPlannerRangeUsingLoadedPhysicalSegments(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(3,2,4));ProjectWork work=new ProjectWork(npc);
        // The elevated strip is outside all neighboring test templates. Chunk
        // loading belongs only to this fixture; ProjectWork cannot load chunks.
        BlockPos start=c.getAbsolutePos(new BlockPos(12,34,12)).add(0,0,-256),destination=start.add(0,0,64);
        for(int chunkX=(start.getX()-4)>>4;chunkX<=(start.getX()+4)>>4;chunkX++)
            for(int chunkZ=(start.getZ()-4)>>4;chunkZ<=(start.getZ()+70)>>4;chunkZ++)
                c.getWorld().setChunkForced(chunkX,chunkZ,true);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=69;z++){
            BlockPos floor=start.add(x,-1,z);c.getWorld().getChunk(floor);
            c.getWorld().setBlockState(floor,Blocks.STONE.getDefaultState());
        }
        npc.refreshPositionAndAngles(start.getX()+0.5,start.getY(),start.getZ()+0.5,0,0);npc.setVelocity(Vec3d.ZERO);
        work.setBoundary(start,100);Vec3d initial=npc.getPos();Vec3d[] previous={initial};boolean[] arrived={false};
        c.runAtEveryTick(()->{
            c.assertTrue(npc.getPos().squaredDistanceTo(previous[0])<1,"Segment navigation teleported the actor");
            previous[0]=npc.getPos();
            if(!arrived[0])arrived[0]=work.moveTo(destination);
        });
        c.runAtTick(620,()->{
            c.assertTrue(arrived[0],"Long route never physically reached its final segment: "+work.status()+" pos="+npc.getPos());
            c.assertTrue(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<0.65
                && npc.getPos().squaredDistanceTo(initial)>60*60,"Intermediate waypoint counted as final arrival");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=220)
    public void stepsOffOneBlockMoundBeforeMiningItsOwnSupport(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(5,3,5));ProjectWork work=new ProjectWork(npc);
        BlockPos mound=c.getAbsolutePos(new BlockPos(5,2,5));
        c.getWorld().setBlockState(mound,Blocks.DIRT.getDefaultState());
        npc.refreshPositionAndAngles(mound.getX()+0.5,mound.getY()+1,mound.getZ()+0.5,0,0);
        npc.setVelocity(Vec3d.ZERO);Vec3d start=npc.getPos();Vec3d[] previous={start};boolean[] finished={false};
        c.runAtEveryTick(()->{
            c.assertTrue(npc.getPos().squaredDistanceTo(previous[0])<1,"Leaving the support teleported the actor");
            previous[0]=npc.getPos();
            if(!finished[0])finished[0]=work.safeMine(mound);
            if(c.getWorld().getBlockState(mound).isAir()){
                c.assertTrue(npc.getPos().squaredDistanceTo(start)>0.25,
                    "Builder removed its support before physically moving off the mound");
                Box body=npc.getBoundingBox();
                c.assertTrue(body.maxX<=mound.getX() || body.minX>=mound.getX()+1
                    || body.maxZ<=mound.getZ() || body.minZ>=mound.getZ()+1,
                    "Builder removed the mound while part of its body still overlapped the support");
            }
        });
        c.runAtTick(180,()->{
            c.assertTrue(finished[0] && c.getWorld().getBlockState(mound).isAir(),
                "Builder could not leave its one-block mound: "+work.status()+" pos="+npc.getPos());
            int drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(mound).expand(3),
                e->e.isAlive() && e.getStack().isOf(Items.DIRT)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(drops==1 && npc.backpack.count(Items.DIRT)==0,
                "Mound preparation must spawn one real dirt drop instead of granting inventory items");
            c.assertTrue(c.getWorld().getBlockState(mound.down()).isOf(Blocks.DIRT),"Builder mined deeper than the requested mound");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="project_work",tickLimit=180)
    public void shiftsFractionalStanceToPlaceTheSeventhFloorBlock(TestContext c){
        CompanionEntity npc=worker(c,new BlockPos(15,2,19));ProjectWork work=new ProjectWork(npc);
        BlockPos anchor=c.getAbsolutePos(new BlockPos(17,2,15)),target=anchor.add(0,0,6);
        for(int z=0;z<6;z++){
            BlockPos floor=anchor.add(0,0,z);c.getWorld().setBlockState(floor,Blocks.SPRUCE_PLANKS.getDefaultState());
            OwnedBlocks.get(c.getWorld()).claim(floor,npc.getUuid());
        }
        npc.refreshPositionAndAngles(anchor.getX()-1.500667,anchor.getY(),anchor.getZ()+4.49758,0,0);
        npc.setVelocity(Vec3d.ZERO);npc.backpack.setStack(0,new ItemStack(Items.SPRUCE_PLANKS));
        Vec3d initial=npc.getPos();boolean[] armed={false},placed={false};Vec3d[] previous={initial};
        c.runAtTick(5,()->{
            var hit=c.getWorld().raycast(new RaycastContext(npc.getEyePos(),Vec3d.ofCenter(target),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
            c.assertTrue(hit.getType()!=HitResult.Type.MISS && hit.getBlockPos().equals(anchor.add(0,0,5)),
                "Fixture did not reproduce the earlier floor occluding the fractional stance");
            armed[0]=true;
        });
        c.runAtEveryTick(()->{
            c.assertTrue(npc.getPos().squaredDistanceTo(previous[0])<1,"Work stance adjustment teleported the actor");
            previous[0]=npc.getPos();
            if(armed[0] && !placed[0])placed[0]=work.place(target,Blocks.SPRUCE_PLANKS.getDefaultState(),Items.SPRUCE_PLANKS,1);
        });
        c.runAtTick(140,()->{
            c.assertTrue(placed[0] && c.getWorld().getBlockState(target).isOf(Blocks.SPRUCE_PLANKS),
                "Builder stalled at the fractional floor corner: "+work.status()+" pos="+npc.getPos());
            c.assertTrue(npc.backpack.count(Items.SPRUCE_PLANKS)==0
                && OwnedBlocks.get(c.getWorld()).owned(target,npc.getUuid()),"Floor placement lost or duplicated its real material");
            c.assertTrue(npc.getPos().squaredDistanceTo(initial)>0.00001,"Builder never physically shifted into a usable sight line");
            for(int z=0;z<6;z++)c.assertTrue(c.getWorld().getBlockState(anchor.add(0,0,z)).isOf(Blocks.SPRUCE_PLANKS),
                "Builder destroyed the previously completed floor to bypass its sight line");
            c.complete();
        });
    }
}
