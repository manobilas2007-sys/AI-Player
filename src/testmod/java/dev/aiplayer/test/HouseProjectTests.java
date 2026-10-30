package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.ai.tasks.HouseBlueprint;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Survival construction is checked against real world effects and inventory,
 * including the natural terrain that the player did not prepare in advance. */
public final class HouseProjectTests implements FabricGameTest {
    private static final BlockPos ORDER=new BlockPos(8,2,8);
    private static final BlockPos ANCHOR=new BlockPos(10,2,8);

    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        }
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(6,2,8));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=true;
        return npc;
    }
    private int owned(TestContext c,CompanionEntity npc,Item item){
        int result=0;
        for(int x=9;x<=15;x++)for(int y=0;y<=7;y++)for(int z=5;z<=16;z++){
            BlockPos pos=c.getAbsolutePos(new BlockPos(x,y,z));
            if(c.getWorld().getBlockState(pos).getBlock().asItem()==item
                    && OwnedBlocks.get(c.getWorld()).owned(pos,npc.getUuid()))result++;
        }
        return result;
    }
    private void give(CompanionEntity npc,Item item,int amount){
        while(amount>0){
            int count=Math.min(64,amount);
            ItemStack remainder=npc.backpack.addStack(new ItemStack(item,count));
            if(!remainder.isEmpty())throw new AssertionError("Fixture inventory does not fit building materials");
            amount-=count;
        }
    }
    private Map<Item,Integer> stockHouse(CompanionEntity npc){
        Map<Item,Integer> material=new LinkedHashMap<>();
        for(var part:HouseBlueprint.plan(BlockPos.ORIGIN,HouseBlueprint.palette("spruce")))
            material.merge(part.item(),1,Integer::sum);
        material.forEach((item,count)->give(npc,item,count));
        return material;
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=3200)
    public void detailedShelterPreparesSnowGrassAndUnevenTerrainUsingOneWood(TestContext c){
        CompanionEntity npc=worker(c);
        Map<Item,Integer> initial=stockHouse(npc);
        give(npc,Items.COBBLESTONE,2);give(npc,Items.OAK_PLANKS,64);
        give(npc,Items.DIAMOND_PICKAXE,1);give(npc,Items.DIAMOND_SHOVEL,1);give(npc,Items.DIAMOND_AXE,1);
        // The site has two support holes, a small dirt mound, vegetation and
        // snow. Its foundation must be paid for and prepared physically.
        c.setBlockState(new BlockPos(10,1,12),Blocks.AIR);
        c.setBlockState(new BlockPos(10,1,13),Blocks.AIR);
        c.setBlockState(new BlockPos(11,2,9),Blocks.DIRT);
        c.setBlockState(new BlockPos(11,3,9),Blocks.GRASS);
        c.setBlockState(new BlockPos(13,2,12),Blocks.GRASS);
        c.setBlockState(new BlockPos(12,2,10),Blocks.SNOW);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),Items.SPRUCE_PLANKS);
        c.runAtTick(3000,()->{
            c.assertFalse(npc.tasks().active(),"Detailed house did not complete: "+npc.tasks().diagnostics());
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(10,1,12))).isOf(Blocks.COBBLESTONE)
                    && c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(10,1,13))).isOf(Blocks.COBBLESTONE),
                "Uneven terrain was left unsupported instead of receiving a real foundation");
            c.assertTrue(npc.backpack.count(Items.COBBLESTONE)==0 && owned(c,npc,Items.COBBLESTONE)==2,
                "Foundation duplicated or lost cobblestone");
            for(int x=0;x<5;x++)for(int z=0;z<7;z++)c.assertTrue(
                c.getWorld().getBlockState(c.getAbsolutePos(ANCHOR.add(x,0,z))).isOf(Blocks.SPRUCE_PLANKS),
                "Snow, grass or dirt prevented the chosen wooden floor");
            BlockPos door=c.getAbsolutePos(HouseBlueprint.door(ANCHOR));
            var lower=c.getWorld().getBlockState(door);var upper=c.getWorld().getBlockState(door.up());
            c.assertTrue(lower.isOf(Blocks.SPRUCE_DOOR) && upper.isOf(Blocks.SPRUCE_DOOR)
                    && lower.get(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                    && upper.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Shelter has no valid real two-block door");
            c.assertTrue(owned(c,npc,Items.GLASS)==5,"Shelter has no five real glass windows");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(ANCHOR.add(0,3,3))).isOf(Blocks.SPRUCE_STAIRS),
                "Pitched roof has no sloping stair eave");
            var ridge=c.getWorld().getBlockState(c.getAbsolutePos(ANCHOR.add(2,4,3)));
            c.assertTrue(ridge.isOf(Blocks.SPRUCE_SLAB) && ridge.get(SlabBlock.TYPE)==SlabType.TOP,
                "Pitched roof has no raised slab ridge");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(HouseBlueprint.workbench(ANCHOR))).isOf(Blocks.CRAFTING_TABLE)
                    && c.getWorld().getBlockState(c.getAbsolutePos(HouseBlueprint.furnace(ANCHOR))).isOf(Blocks.FURNACE),
                "Shelter lacks its real interior workbench/furnace");
            c.assertTrue(npc.backpack.count(Items.OAK_PLANKS)==64 && owned(c,npc,Items.OAK_PLANKS)==0,
                "Construction switched to a different available wood species");
            for(var quantity:initial.entrySet())c.assertTrue(npc.backpack.count(quantity.getKey())==0,
                "Completed house did not consume the exact supplied amount of "+quantity.getKey());
            c.assertTrue(npc.getZ()<c.getAbsolutePos(ANCHOR).getZ(),"Builder did not leave through the entrance");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=130)
    public void protectedConstructionBlocksHouseBeforeAnyMaterialIsSpent(TestContext c){
        CompanionEntity npc=worker(c);give(npc,Items.SPRUCE_PLANKS,32);
        BlockPos blocked=c.getAbsolutePos(ANCHOR);
        c.getWorld().setBlockState(blocked,Blocks.BRICKS.getDefaultState());
        OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),blocked);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),Items.SPRUCE_PLANKS);
        c.runAtTick(100,()->{
            c.assertTrue(npc.tasks().active(),"Blocked house falsely claimed completion");
            c.assertTrue(c.getWorld().getBlockState(blocked).isOf(Blocks.BRICKS),"House erased the player's construction");
            c.assertTrue(npc.backpack.count(Items.SPRUCE_PLANKS)==32 && owned(c,npc,Items.SPRUCE_PLANKS)==0,
                "Preflight consumed materials before resolving a protected obstruction");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=130)
    public void missingResourcesRemainARealUnfinishedProject(TestContext c){
        CompanionEntity npc=worker(c);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),Items.SPRUCE_PLANKS);
        c.runAtTick(100,()->{
            c.assertTrue(npc.tasks().active() && npc.tasks().progress()==0,"Missing materials were treated as a completed house");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(ANCHOR)).isAir()
                    && npc.backpack.isEmpty(),"House invented a floor or inventory without its materials");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=240)
    public void selectedWoodSurvivesReloadAndCannotUseAnotherSpecies(TestContext c){
        CompanionEntity npc=worker(c);give(npc,Items.SPRUCE_PLANKS,6);give(npc,Items.OAK_PLANKS,64);
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),Items.SPRUCE_PLANKS);
        c.runAtTick(100,()->{
            c.assertTrue(npc.tasks().progress()>0,"House did not build a real partial floor before saving");
            NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
            c.assertTrue(saved.getCompound("DetailedHouse").getString("Wood").equals("spruce"),"Saved project lost its palette");
            npc.tasks().cancel();npc.tasks().readNbt(saved);
        });
        c.runAtTick(200,()->{
            NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
            c.assertTrue(npc.tasks().active() && saved.getCompound("DetailedHouse").getString("Wood").equals("spruce"),
                "Reload changed the selected house species");
            c.assertTrue(npc.backpack.count(Items.OAK_PLANKS)==64 && owned(c,npc,Items.OAK_PLANKS)==0,
                "Reload used an unrelated wood as a substitute");
            c.assertTrue(owned(c,npc,Items.SPRUCE_PLANKS)==6,"Reload lost/duplicated the already placed wood");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=60)
    public void emptyInventoryChoosesVisibleLocalTimberOnce(TestContext c){
        CompanionEntity npc=worker(c);
        c.setBlockState(new BlockPos(8,2,10),Blocks.SPRUCE_LOG);
        c.setBlockState(new BlockPos(8,3,10),Blocks.SPRUCE_LOG);
        c.setBlockState(new BlockPos(8,4,10),Blocks.SPRUCE_LEAVES);
        // Keep this observation-only fixture from becoming another parallel
        // project's resource target in the neighboring test arena.
        OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(new BlockPos(8,2,10)),npc.getUuid());
        OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(new BlockPos(8,3,10)),npc.getUuid());
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),null);
        c.runAtTick(20,()->{
            NbtCompound saved=new NbtCompound();npc.tasks().writeNbt(saved);
            c.assertTrue(saved.getCompound("DetailedHouse").getString("Wood").equals("spruce"),
                "An empty inventory demanded oak instead of choosing the nearby natural spruce");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(8,2,10))).isIn(BlockTags.LOGS),
                "Palette selection remotely destroyed its observed tree");
            npc.tasks().cancel();c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="detailed_house",tickLimit=700)
    public void missingFloorWoodIsPhysicallyGatheredAndConvertedWithoutSubstitution(TestContext c){
        CompanionEntity npc=worker(c);npc.autoCraft=true;
        give(npc,Items.DIAMOND_AXE,1);give(npc,Items.DIAMOND_PICKAXE,1);give(npc,Items.DIAMOND_SWORD,1);
        give(npc,Items.CRAFTING_TABLE,1);give(npc,Items.STICK,4);give(npc,Items.OAK_PLANKS,64);
        for(int y=2;y<=4;y++)c.setBlockState(new BlockPos(6,y,13),Blocks.SPRUCE_LOG);
        for(int x=5;x<=7;x++)for(int z=12;z<=14;z++)c.setBlockState(new BlockPos(x,5,z),
            Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE,1));
        npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,c.getAbsolutePos(ORDER),Items.SPRUCE_PLANKS);
        c.runAtTick(600,()->{
            int floor=owned(c,npc,Items.SPRUCE_PLANKS),remaining=0;
            for(int y=2;y<=4;y++)if(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(6,y,13))).isOf(Blocks.SPRUCE_LOG))remaining++;
            c.assertTrue(floor>=4 && remaining<3,
                "House did not obtain its missing selected wood from a real tree: "+npc.tasks().diagnostics());
            int logs=npc.backpack.count(Items.SPRUCE_LOG)
                +(npc.getMainHandStack().isOf(Items.SPRUCE_LOG)?npc.getMainHandStack().getCount():0);
            int planks=npc.backpack.count(Items.SPRUCE_PLANKS)
                +(npc.getMainHandStack().isOf(Items.SPRUCE_PLANKS)?npc.getMainHandStack().getCount():0);
            BlockPos tree=c.getAbsolutePos(new BlockPos(6,3,13));
            int dropped=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(tree).expand(12),
                item->item.isAlive() && item.getStack().isOf(Items.SPRUCE_LOG)).stream().mapToInt(item->item.getStack().getCount()).sum();
            c.assertTrue(floor+planks+4*(remaining+logs+dropped)==12,
                "Tree-to-floor dependency fabricated or lost the original three logs");
            c.assertTrue(npc.backpack.count(Items.OAK_PLANKS)==64 && owned(c,npc,Items.OAK_PLANKS)==0,
                "Resource acquisition substituted the wrong available wood");
            c.assertTrue(npc.tasks().active(),"A partial floor falsely completed the detailed house");
            npc.tasks().cancel();c.complete();
        });
    }
}
