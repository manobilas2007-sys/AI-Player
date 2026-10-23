package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.*;

/** In-game regression fixtures for genuinely navigable, non-vertical excavations. */
public final class ExcavationTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,0,z),Blocks.DIRT);
        }
        CompanionEntity a=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(4,2,4));
        a.setMode(Mode.WAIT);a.autoSurvive=false;a.autoCraft=false;
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        return a;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=40)
    public void declinesShaftWithoutStepSupport(TestContext c){
        CompanionEntity a=worker(c);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,0,z),Blocks.AIR);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->{c.assertFalse(e.beginStair(),"Attempted descent into unsupported shaft");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=40)
    public void preservesPlayerCraftingTables(TestContext c){
        CompanionEntity a=worker(c);
        for(Direction d:Direction.Type.HORIZONTAL)c.setBlockState(new BlockPos(4,1,4).offset(d),Blocks.CRAFTING_TABLE);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->{c.assertFalse(e.beginStair(),"Started digging through a crafting table");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=40)
    public void rejectsLavaBehindWall(TestContext c){
        CompanionEntity a=worker(c);
        c.setBlockState(new BlockPos(5,2,4),Blocks.STONE);
        c.setBlockState(new BlockPos(6,2,4),Blocks.LAVA);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->{c.assertFalse(e.beginGallery(c.getAbsolutePos(new BlockPos(5,2,4))),"Tunnel opened into hidden lava");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=320)
    public void cutsGalleryTwoHighTwoWideWithoutMiningFloor(TestContext c){
        CompanionEntity a=worker(c);
        
        BlockPos base=new BlockPos(4,2,4);
        for(int z=3;z<=4;z++)for(int y=2;y<=3;y++)c.setBlockState(new BlockPos(5,y,z),Blocks.STONE);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->c.assertTrue(e.beginGallery(c.getAbsolutePos(new BlockPos(5,2,4))),"Valid gallery refused"));
        for(int t=6;t<290;t++)c.runAtTick(t,e::tick);
        c.runAtTick(300,()->{
            for(int z=3;z<=4;z++)for(int y=2;y<=3;y++)
                c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(5,y,z))).isAir(),"Gallery not excavated at "+y+","+z);
            for(int z=3;z<=4;z++)c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(5,1,z))).isOf(Blocks.DIRT),"Excavated gallery floor");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,1,4))).isOf(Blocks.DIRT),"Removed current support");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=200)
    public void ceilingStoneCanBeMinedWithoutTouchingFooting(TestContext c){
        CompanionEntity a=worker(c);
        BlockPos ceiling=new BlockPos(4,4,4);
        c.setBlockState(ceiling,Blocks.STONE);
        SurfaceStoneGathering gather=new SurfaceStoneGathering(a);
        for(int t=2;t<155;t++)c.runAtTick(t,gather::tick);
        c.runAtTick(170,()->{
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(ceiling)).isAir(),"Safe ceiling stone was not mined");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,1,4))).isOf(Blocks.DIRT),"Ceiling mining destroyed the footing");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=30)
    public void allowsHigherTowerWhenDescentMaterialIsReserved(TestContext c){
        CompanionEntity a=worker(c);
        // A taller pillar is permitted when one real inventory block can be
        // reused as a temporary side ledge during physical reverse teardown.
        var blocks=new net.minecraft.nbt.NbtList();
        for(int y=2;y<=3;y++){
            BlockPos relative=new BlockPos(4,y,4);
            c.setBlockState(relative,Blocks.SPRUCE_LOG);
            var n=new net.minecraft.nbt.NbtCompound();
            n.putLong("Pos",c.getAbsolutePos(relative).asLong());
            n.putString("Block","minecraft:spruce_log");blocks.add(n);
        }
        a.refreshPositionAndAngles(c.getAbsolutePos(new BlockPos(4,4,4)).getX()+0.5,
            c.getAbsolutePos(new BlockPos(4,4,4)).getY(),
            c.getAbsolutePos(new BlockPos(4,4,4)).getZ()+0.5,0,0);
        a.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,4));
        var data=new net.minecraft.nbt.NbtCompound();data.put("TemporaryPillar",blocks);
        TemporaryPillar pillar=new TemporaryPillar(a);pillar.readNbt(data);
        c.runAtTick(12,()->{
            c.assertTrue(a.isOnGround(),"Test NPC did not land on the second support");
            c.assertTrue(pillar.climb(),"A third support was refused despite having a reserved descent block");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=25)
    public void naturalDioriteDoesNotBlockGalleryButPlayerBenchDoes(TestContext c){
        CompanionEntity a=worker(c);
        BlockPos front=new BlockPos(5,2,4);
        c.setBlockState(front,Blocks.STONE);
        c.setBlockState(new BlockPos(5,2,3),Blocks.DIORITE);
        c.assertTrue(SafeExcavation.isExcavatableMaterial(Blocks.DIORITE.getDefaultState()),
            "Diorite should not stop a gallery");
        c.assertTrue(SafeExcavation.isExcavatableMaterial(Blocks.ANDESITE.getDefaultState()),
            "Andesite should not stop a gallery");
        c.assertFalse(SafeExcavation.isExcavatableMaterial(Blocks.CRAFTING_TABLE.getDefaultState()),
            "Player's crafting table should never be cleared as mining material");
        c.assertFalse(SafeExcavation.isExcavatableMaterial(Blocks.DIAMOND_ORE.getDefaultState()),
            "Ore should not be automatically demolished as corridor filler");
        SafeExcavation excavation=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->{c.assertTrue(excavation.beginGallery(c.getAbsolutePos(front)),
            "Safe gallery rejected because diorite occupies its adjacent lane");c.complete();});
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=160)
    public void descendingStairPreservesCurrentFloorAndNextFooting(TestContext c){
        CompanionEntity a=worker(c);
        Direction facing=a.getHorizontalFacing();
        BlockPos next=new BlockPos(4,2,4).offset(facing);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->c.assertTrue(e.beginStair(),"Safe supported stair refused"));
        for(int t=6;t<125;t++)c.runAtTick(t,e::tick);
        c.runAtTick(135,()->{
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(next.down())).isAir(),"Stair step not removed");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(next.down(2))).isOf(Blocks.DIRT),"Excavated the stair footing");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,1,4))).isOf(Blocks.DIRT),"Excavated NPC support");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="excavation",tickLimit=850)
    public void walksThroughDioriteSeamToReachMoreStone(TestContext c){
        CompanionEntity a=worker(c);
        for(int x=5;x<=7;x++)for(int z=3;z<=4;z++)for(int y=2;y<=3;y++)
            c.setBlockState(new BlockPos(x,y,z),x==6?Blocks.DIORITE:Blocks.STONE);
        SafeExcavation e=new SafeExcavation(a,new PhysicalMining(a,SurfaceStoneGathering::suitablePick));
        c.runAtTick(5,()->c.assertTrue(e.beginGallery(c.getAbsolutePos(new BlockPos(5,2,4))),"Gallery start failed"));
        for(int t=6;t<750;t++)c.runAtTick(t,e::tick);
        c.runAtTick(780,()->{
            for(int x=5;x<=7;x++)for(int z=3;z<=4;z++){
                for(int y=2;y<=3;y++)c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,y,z))).isAir(),"Stopped before clearing seam at "+x+","+y+","+z+" npc="+a.getPos());
                c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,1,z))).isOf(Blocks.DIRT),"Removed gallery floor");
            }
            c.complete();
        });
    }

}
