package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.ResourceGathering;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.brain.AutonomousSurvey;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Hunting orders and passive observations must have real, bounded effects. */
public final class FoodAutonomyTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(15,2,15));
        NbtCompound saved=new NbtCompound();npc.writeCustomDataToNbt(saved);
        saved.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(saved);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoEquip=false;
        npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        return npc;
    }
    private CowEntity cow(TestContext c,BlockPos relative){
        CowEntity cow=EntityType.COW.create(c.getWorld());
        BlockPos position=c.getAbsolutePos(relative);
        cow.refreshPositionAndAngles(position.getX()+0.5,position.getY(),position.getZ()+0.5,0,0);
        cow.setAiDisabled(true);c.getWorld().spawnEntity(cow);return cow;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="food_autonomy",tickLimit=240)
    public void manualFoodHuntsBeforePursuingUnrelatedWood(TestContext c){
        CompanionEntity npc=worker(c);CowEntity wild=cow(c,new BlockPos(21,2,15));
        BlockPos spot=c.getAbsolutePos(new BlockPos(9,2,15));
        ItemEntity logs=new ItemEntity(c.getWorld(),spot.getX()+0.5,spot.getY(),spot.getZ()+0.5,
            new ItemStack(Items.SPRUCE_LOG,3));
        logs.setVelocity(Vec3d.ZERO);logs.setPickupDelay(0);c.getWorld().spawnEntity(logs);
        npc.gathering().start(ResourceGathering.Kind.FOOD,1,true);
        c.runAtTick(200,()->{
            c.assertFalse(wild.isAlive(),"A reachable wild animal was not hunted");
            c.assertTrue(npc.backpack.count(Items.BEEF)>=1,"The hunt did not physically collect its food drops");
            c.assertTrue(npc.backpack.count(Items.SPRUCE_LOG)==0 && logs.isAlive(),
                "The hunting order was diverted to unrelated wood");
            c.assertFalse(npc.gathering().active(),"Verified food collection did not finish the order");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="food_autonomy",tickLimit=260)
    public void foodHuntPreservesNamedAndPersistentAnimals(TestContext c){
        CompanionEntity npc=worker(c);
        CowEntity pet=cow(c,new BlockPos(17,2,17));pet.setCustomName(Text.literal("Mimosa"));
        CowEntity kept=cow(c,new BlockPos(19,2,18));kept.setPersistent();
        CowEntity wild=cow(c,new BlockPos(22,2,15));
        npc.gathering().start(ResourceGathering.Kind.FOOD,1,true);
        c.runAtTick(220,()->{
            c.assertTrue(pet.isAlive() && pet.getHealth()==pet.getMaxHealth(),"Named livestock was attacked");
            c.assertTrue(kept.isAlive() && kept.getHealth()==kept.getMaxHealth(),"Persistent livestock was attacked");
            c.assertFalse(wild.isAlive(),"The hunter did not select the unowned adult animal");
            c.assertTrue(npc.backpack.count(Items.BEEF)>=1,"Wild prey drops were never physically collected");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="food_autonomy",tickLimit=110)
    public void passiveSurveyRemembersVisibleOreWithoutSeeingThroughWalls(TestContext c){
        CompanionEntity npc=worker(c);AutonomousSurvey survey=new AutonomousSurvey(npc);
        BlockPos visible=new BlockPos(17,2,15),buried=new BlockPos(15,2,19);
        c.setBlockState(visible,Blocks.IRON_ORE);c.setBlockState(buried,Blocks.IRON_ORE);
        for(int x=13;x<=17;x++)for(int y=2;y<=5;y++)c.setBlockState(new BlockPos(x,y,17),Blocks.STONE);
        for(int t=2;t<=90;t+=5)c.runAtTick(t,()->{
            survey.observe();
            c.assertTrue(survey.lastObservedCells()<=96 && survey.lastSightChecks()<=2,
                "Passive observation exceeded its per-tick work budget");
        });
        c.runAtTick(100,()->{
            var memory=npc.intelligence().memory();long now=c.getWorld().getTime();
            c.assertTrue(memory.nearest("iron",c.getAbsolutePos(visible),now,0).isPresent(),
                "Visible nearby ore was not remembered by autonomous observation");
            c.assertTrue(memory.nearest("iron",c.getAbsolutePos(buried),now,0).isEmpty(),
                "Survey detected ore through an opaque wall");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(visible)).isOf(Blocks.IRON_ORE)
                    && c.getWorld().getBlockState(c.getAbsolutePos(buried)).isOf(Blocks.IRON_ORE)
                    && npc.backpack.isEmpty(),"Observation mined or fabricated a resource");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="food_autonomy")
    public void idleSurveyDoesNotOverrideAnUnownedMovementRoute(TestContext c){
        CompanionEntity npc=worker(c);AutonomousSurvey survey=new AutonomousSurvey(npc);
        BlockPos destination=c.getAbsolutePos(new BlockPos(20,2,15));
        var route=WorkNavigation.findPath(npc,destination,0);
        c.assertTrue(route!=null && npc.getNavigation().startMovingAlong(route,1),"Fixture route is unavailable");
        c.assertFalse(survey.idleStep(),"Idle exploration activated without its owner present");
        c.assertTrue(npc.getNavigation().getCurrentPath()==route && !npc.getNavigation().isIdle(),
            "Survey cancelled another activity's navigation");
        c.complete();
    }
}
