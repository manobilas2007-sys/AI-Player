package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.ResourceGathering;
import dev.aiplayer.ai.SurfaceStoneGathering;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public final class ManualResourceSurveyTests implements FabricGameTest {
    private record Fixture(CompanionEntity npc,BlockPos origin,BlockPos resource) {}
    private Fixture ground(TestContext c){
        // The owner is offline and the fixture is outside the GameTest arena's
        // ticking region. Only the NPC's own moving tickets keep it working.
        BlockPos origin=c.getAbsolutePos(new BlockPos(4096,2,4096));
        for(int x=-60;x<=110;x++)for(int z=-60;z<=110;z++){
            c.getWorld().setBlockState(origin.add(x,-1,z),Blocks.DIRT.getDefaultState());
            for(int y=0;y<=7;y++)c.getWorld().setBlockState(origin.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        CompanionEntity npc=AIPlayer.COMPANION.create(c.getWorld());
        npc.refreshPositionAndAngles(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
        npc.autoCraft=false;npc.autoSurvive=false;npc.autoLoot=true;npc.autoEquip=true;npc.setMode(Mode.WAIT);
        c.getWorld().spawnEntity(npc);
        return new Fixture(npc,origin,origin.add(76,0,-24));
    }
    private String debug(Fixture f){
        return " pos="+f.npc().getPos()+" origin="+f.origin()+" brain="+f.npc().intelligence().status()
            +" search="+f.npc().gathering().searchStatus()+" progress="+f.npc().gathering().progress();
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="manual_long_wood",tickLimit=6000)
    public void manualWoodOrderFindsAndCollectsTreeBeyondFiftyBlocksWithoutOwner(TestContext c){
        Fixture f=ground(c);CompanionEntity npc=f.npc();
        for(int y=0;y<3;y++)c.getWorld().setBlockState(f.resource().up(y),Blocks.OAK_LOG.getDefaultState());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            c.getWorld().setBlockState(f.resource().add(x,3,z),Blocks.OAK_LEAVES.getDefaultState());
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        npc.gathering().start(ResourceGathering.Kind.WOOD,1,true);
        c.runAtEveryTick(()->{
            if(npc.gathering().active())return;
            c.assertTrue(npc.gathering().progress()>=1 && npc.backpack.count(Items.OAK_LOG)>=1,
                "Manual wood exploration ended without physical collection"+debug(f));
            c.assertTrue(npc.getBlockPos().getSquaredDistance(f.origin())>50*50
                && c.getWorld().getBlockState(f.resource()).isAir(),"Distant natural tree was not physically harvested");
            npc.discard();c.complete();
        });
        c.runAtTick(5900,()->{c.assertFalse(npc.gathering().active(),"Wood survey never found distant resource"+debug(f));npc.discard();c.complete();});
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="manual_long_food",tickLimit=6000)
    public void manualFoodOrderFindsHuntsAndCollectsBeyondFiftyBlocksWithoutOwner(TestContext c){
        Fixture f=ground(c);CompanionEntity npc=f.npc();
        var pig=EntityType.PIG.create(c.getWorld());pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(f.resource().getX()+0.5,f.resource().getY(),f.resource().getZ()+0.5,0,0);
        c.getWorld().spawnEntity(pig);
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        npc.gathering().start(ResourceGathering.Kind.FOOD,1,true);
        c.runAtEveryTick(()->{
            if(npc.gathering().active())return;
            c.assertTrue(!pig.isAlive() && npc.gathering().progress()>=1 && npc.backpack.count(Items.PORKCHOP)>=1,
                "Manual food exploration ended without hunting and physical pickup"+debug(f));
            c.assertTrue(npc.getBlockPos().getSquaredDistance(f.origin())>50*50,"Food was remotely inserted");
            npc.discard();pig.discard();c.complete();
        });
        c.runAtTick(5900,()->{c.assertFalse(npc.gathering().active(),"Food survey never reached distant prey"+debug(f));npc.discard();pig.discard();c.complete();});
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="manual_long_stone",tickLimit=6000)
    public void manualStoneOrderFindsNaturalCaveAndMinesBeyondFiftyBlocks(TestContext c){
        Fixture f=ground(c);CompanionEntity npc=f.npc();
        for(int x=64;x<=88;x++)for(int z=-31;z<=-17;z++)
            c.getWorld().setBlockState(f.origin().add(x,5,z),Blocks.STONE.getDefaultState());
        c.getWorld().setBlockState(f.resource(),Blocks.STONE.getDefaultState());
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        c.assertFalse(SurfaceStoneGathering.caveAccess(npc,npc.getBlockPos()),"Stone fixture did not start outside a cave");
        npc.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.assertTrue(npc.gathering().active(),"Surface stone order was rejected instead of exploring");
        c.runAtEveryTick(()->{
            if(npc.gathering().active())return;
            c.assertTrue(npc.gathering().progress()==1 && npc.backpack.count(Items.COBBLESTONE)==1,
                "Stone exploration ended without real mining and pickup"+debug(f));
            c.assertTrue(npc.getBlockPos().getSquaredDistance(f.origin())>50*50
                && npc.getMainHandStack().getDamage()>0,"Distant cave stone was not physically mined"+debug(f));
            npc.discard();c.complete();
        });
        c.runAtTick(5900,()->{c.assertFalse(npc.gathering().active(),"Stone survey never found and mined cave resource"+debug(f));npc.discard();c.complete();});
    }
}
