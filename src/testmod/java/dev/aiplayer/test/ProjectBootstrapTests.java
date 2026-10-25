package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.ResourceGathering;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.ai.tasks.HouseBlueprint;
import dev.aiplayer.commands.CompanionDialogue;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** End-to-end orders start with no supplied item, tool or station. Every
 * ingredient must come from the surrounding natural blocks, through ordinary
 * entity ticks and the same dialogue/brain arbitration used by a real owner. */
public final class ProjectBootstrapTests implements FabricGameTest {
    private static final BlockPos ORDER=new BlockPos(24,2,24);
    private static final int INITIAL_LOGS=5*8*3;
    private record Worker(CompanionEntity npc,ServerPlayerEntity owner) {}

    private Worker world(TestContext c){
        for(int x=0;x<48;x++)for(int z=0;z<48;z++){
            c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
            c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
            c.setBlockState(new BlockPos(x,2,z),Blocks.GRASS);
        }
        // Low, spaced natural trees are reachable from real ground. No tree
        // or quarry overlaps either reserved construction footprint.
        for(int x=4;x<=20;x+=4)for(int z=4;z<=32;z+=4){
            for(int y=2;y<=4;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.OAK_LOG);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                c.setBlockState(new BlockPos(x+dx,5,z+dz),
                    Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE,1));
        }
        for(int z=6;z<=20;z++)for(int x:new int[]{22,24})
            c.setBlockState(new BlockPos(x,2,z),Blocks.STONE);
        for(int z=6;z<=14;z+=2){
            c.setBlockState(new BlockPos(26,2,z),Blocks.COAL_ORE);
            c.setBlockState(new BlockPos(28,2,z),Blocks.IRON_ORE);
            // Sand rests on paid-for fixture ground, with dry air around it.
            c.setBlockState(new BlockPos(30,2,z),Blocks.SAND);
        }
        // Independent river sources cannot regenerate each other. The farm
        // therefore has to drain actual water for all three deliveries.
        for(int x:new int[]{34,37,40})c.setBlockState(new BlockPos(x,1,18),Blocks.WATER);

        var owner=c.createMockCreativeServerPlayerInWorld();
        Vec3d at=Vec3d.ofBottomCenter(c.getAbsolutePos(ORDER));
        owner.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(22,2,24));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",owner.getUuid());npc.readCustomDataFromNbt(saved);
        npc.backpack.clear();npc.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        npc.setMode(Mode.FOLLOW);npc.autoCraft=true;npc.autoEquip=true;npc.autoLoot=true;npc.autoSurvive=true;
        c.assertTrue(npc.backpack.isEmpty() && npc.getMainHandStack().isEmpty(),
            "Bootstrap fixture supplied an initial item");
        return new Worker(npc,owner);
    }
    private void confirm(TestContext c,Worker worker,String order,CompanionTasks.Kind expected){
        c.assertTrue(CompanionDialogue.handle(order,worker.owner(),worker.npc()),"Ordinary dialogue did not accept the project order");
        c.assertFalse(worker.npc().tasks().active(),"Project changed the world before the owner's confirmation");
        c.assertTrue(CompanionDialogue.handle("pode",worker.owner(),worker.npc()),"Ordinary dialogue did not accept confirmation");
        c.assertTrue(worker.npc().tasks().kind()==expected,"Confirmation started the wrong project");
        c.assertFalse(worker.npc().gathering().active(),"Previous autonomous collection survived the confirmed project");
    }
    private int logs(TestContext c){
        int count=0;
        for(int x=4;x<=20;x+=4)for(int z=4;z<=32;z+=4)for(int y=2;y<=4;y++)
            if(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,y,z))).isOf(Blocks.OAK_LOG))count++;
        return count;
    }
    private int resource(TestContext c,int x,net.minecraft.block.Block block){
        int count=0;for(int z=6;z<=14;z+=2)
            if(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,2,z))).isOf(block))count++;
        return count;
    }
    private int count(CompanionEntity npc,Item item){
        return npc.backpack.count(item)+(npc.getMainHandStack().isOf(item)?npc.getMainHandStack().getCount():0);
    }
    private String diagnostics(CompanionEntity npc){
        return npc.tasks().diagnostics()+" brain="+npc.intelligence().status()+" pos="+npc.getPos()
            +" hand="+npc.getMainHandStack()+" bag="+java.util.stream.IntStream.range(0,npc.backpack.size())
                .mapToObj(i->npc.backpack.getStack(i).toString()).toList();
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="bootstrap_house",tickLimit=18000)
    public void confirmedShelterObtainsAndProcessesAllMaterialsFromAnEmptyInventory(TestContext c){
        Worker worker=world(c);CompanionEntity npc=worker.npc();
        BlockPos anchor=c.getAbsolutePos(ORDER.add(2,0,0));
        confirm(c,worker,"construa um abrigo de carvalho",CompanionTasks.Kind.BUILD_HOUSE);
        c.runAtTick(900,()->c.assertTrue(logs(c)<INITIAL_LOGS,
            "Confirmed empty-inventory shelter never started acquiring natural timber: "+diagnostics(npc)));
        c.runAtEveryTick(()->{
            if(npc.tasks().active())return;
            var palette=HouseBlueprint.palette("oak");
            for(var part:HouseBlueprint.plan(anchor,palette)){
                var actual=c.getWorld().getBlockState(part.position());
                c.assertTrue(actual.isOf(part.state().getBlock()) && OwnedBlocks.get(c.getWorld()).owned(part.position(),npc.getUuid()),
                    "Empty-inventory shelter completed without a real owned blueprint part at "+part.position()+": "+diagnostics(npc));
            }
            BlockPos door=HouseBlueprint.door(anchor);
            var upper=c.getWorld().getBlockState(door.up());
            c.assertTrue(upper.isOf(Blocks.OAK_DOOR) && upper.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER,
                "Empty-inventory house did not finish its physical door");
            c.assertTrue(resource(c,30,Blocks.SAND)==0,
                "House windows did not require physically gathering all five natural sand blocks");
            c.assertTrue(logs(c)<INITIAL_LOGS && count(npc,Items.WOODEN_PICKAXE)+count(npc,Items.STONE_PICKAXE)>0,
                "House skipped its real timber/tool dependencies");
            worker.owner().discard();c.complete();
        });
        c.runAtTick(17900,()->{
            c.assertFalse(npc.tasks().active(),"Empty-inventory shelter did not finish: "+diagnostics(npc));
            worker.owner().discard();c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="bootstrap_farm",tickLimit=14000)
    public void confirmedFarmObtainsTimberMinesSmeltsAndReusesItsBucketFromAnEmptyInventory(TestContext c){
        Worker worker=world(c);CompanionEntity npc=worker.npc();
        BlockPos anchor=c.getAbsolutePos(ORDER.add(2,-1,0));
        // A routine food search may already be running when the player speaks,
        // as in the reported failure. Confirmation must give the project control.
        npc.gathering().start(ResourceGathering.Kind.FOOD,3,false);
        confirm(c,worker,"crie uma plantação",CompanionTasks.Kind.CREATE_FARM);
        c.runAtTick(900,()->c.assertTrue(logs(c)<INITIAL_LOGS,
            "Confirmed empty-inventory farm never started acquiring natural timber: "+diagnostics(npc)));
        c.runAtEveryTick(()->{
            if(npc.tasks().active())return;
            c.assertTrue(npc.mode()==Mode.FOLLOW,"Finished empty-inventory farm did not return to following");
            for(int x=0;x<7;x++)for(int z=0;z<7;z++)if(x==0 || x==6 || z==0 || z==6){
                BlockPos edge=anchor.add(x,0,z);
                c.assertTrue(c.getWorld().getBlockState(edge).isOf(Blocks.OAK_LOG)
                        && OwnedBlocks.get(c.getWorld()).owned(edge,npc.getUuid()),
                    "Farm completed without its real single-species timber border");
            }
            for(int z=2;z<=4;z++){
                BlockPos water=anchor.add(3,0,z);
                c.assertTrue(c.getWorld().getBlockState(water).isOf(Blocks.WATER)
                        && c.getWorld().getFluidState(water).isStill(),"Farm completed without all three delivered sources");
            }
            c.assertTrue(count(npc,Items.BUCKET)==1 && count(npc,Items.WATER_BUCKET)==0,
                "Empty-inventory farm did not make and reuse exactly one real bucket");
            c.assertTrue(resource(c,28,Blocks.IRON_ORE)==2,
                "Farm did not obtain exactly three iron inputs from real exposed ore");
            for(int x:new int[]{34,37,40})c.assertFalse(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(x,1,18))).isOf(Blocks.WATER),
                "Farm created its water without draining three independent river sources");
            c.assertTrue(c.getWorld().getBlockState(anchor.add(-2,1,-3)).isOf(Blocks.CRAFTING_TABLE)
                    && c.getWorld().getBlockState(anchor.add(-2,1,-1)).isOf(Blocks.FURNACE),
                "Empty-inventory farm omitted the real crafting/smelting dependency chain");
            worker.owner().discard();c.complete();
        });
        c.runAtTick(13900,()->{
            c.assertFalse(npc.tasks().active(),"Empty-inventory farm did not finish: "+diagnostics(npc));
            worker.owner().discard();c.complete();
        });
    }
}
