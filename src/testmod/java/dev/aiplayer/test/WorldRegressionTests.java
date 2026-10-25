package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import java.util.UUID;

/** Regression cases added after the real-world ice forest/cave report, not replacements for existing tests. */
public final class WorldRegressionTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(2,2,2));
        NbtCompound n=new NbtCompound();npc.writeCustomDataToNbt(n);n.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(n);
        npc.autoCraft=false;npc.autoSurvive=false;return npc;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=600)
    public void stoneCanBeMinedInsideCoveredSafeChamber(TestContext c){
        CompanionEntity a=worker(c);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,6,z),Blocks.STONE);
        c.setBlockState(new BlockPos(4,2,2),Blocks.STONE);
        a.gathering().start(ResourceGathering.Kind.STONE,1,true);
        c.runAtTick(500,()->{
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==1 && !a.gathering().active(),
                "Accessible chamber stone not acquired. " + a.intelligence().status());
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=130)
    public void submergedDropAcceptsShortSwimRoute(TestContext c){
        CompanionEntity a=worker(c);
        BlockPos target=new BlockPos(4,2,2);
        c.setBlockState(new BlockPos(3,2,2),Blocks.WATER);
        c.setBlockState(target,Blocks.WATER);
        BlockPos abs=c.getAbsolutePos(target);
        ItemEntity drop=new ItemEntity(c.getWorld(),abs.getX()+0.5,abs.getY()+0.3,abs.getZ()+0.5,new ItemStack(Items.SPRUCE_LOG,1));
        // Keep the fixture in its water source: ItemEntity otherwise receives a
        // random sideways launch and may leave the pool before the route check.
        // Gravity and buoyancy remain enabled, as in the real pickup scenario.
        drop.setVelocity(Vec3d.ZERO);
        drop.setPickupDelay(0);c.getWorld().spawnEntity(drop);
        a.refreshPositionAndAngles(c.getAbsolutePos(new BlockPos(3,2,2)).getX()+0.5,
            c.getAbsolutePos(new BlockPos(3,2,2)).getY()+0.2,
            c.getAbsolutePos(new BlockPos(3,2,2)).getZ()+0.5,0,0);
        c.runAtTick(10,()->{
            c.assertTrue(WorkNavigation.swimToDrop(a,drop) || a.backpack.count(Items.SPRUCE_LOG)>0,
                "Nearby water drop route refused while swimming: npc="+a.getPos()
                    +" drop="+drop.getPos()+" air="+a.getAir()
                    +" swimming="+a.isTouchingWater()
                    +" dropFluid="+c.getWorld().getFluidState(drop.getBlockPos())
                    +" belowFluid="+c.getWorld().getFluidState(drop.getBlockPos().down()));
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=20)
    public void blockedPickupDoesNotClaimContact(TestContext c){
        CompanionEntity a=worker(c);
        BlockPos dropPos=new BlockPos(4,2,2);
        BlockPos abs=c.getAbsolutePos(dropPos);
        c.setBlockState(new BlockPos(3,2,2),Blocks.STONE);
        c.setBlockState(new BlockPos(3,3,2),Blocks.STONE);
        // The item is close enough for pickup but genuinely behind a 2-block wall.
        ItemEntity drop=new ItemEntity(c.getWorld(),abs.getX()+0.05,abs.getY()+0.5,abs.getZ()+0.5,new ItemStack(Items.OAK_LOG));
        drop.setPickupDelay(0);c.getWorld().spawnEntity(drop);
        c.assertFalse(a.survival().canCollect(drop),"Drop behind wall incorrectly considered collectible");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=130)
    public void isolatedSupportCleanupDefersInsteadOfFreezingWood(TestContext c){
        CompanionEntity a=worker(c);
        a.setAiDisabled(true);
        BlockPos support=new BlockPos(2,1,2);
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)
            if(x!=2 || z!=2)c.setBlockState(new BlockPos(x,1,z),Blocks.AIR);
        c.setBlockState(support,Blocks.COBBLESTONE);
        OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(support),a.getUuid());
        a.gathering().start(ResourceGathering.Kind.WOOD,8,true);
        NbtCompound saved=new NbtCompound();a.gathering().writeNbt(saved);
        NbtList list=new NbtList();NbtCompound block=new NbtCompound();
        block.putLong("Pos",c.getAbsolutePos(support).asLong());block.putString("Block","minecraft:cobblestone");
        list.add(block);saved.put("TemporaryPillar",list);a.gathering().readNbt(saved);
        c.runAtTick(95,()->{
            c.assertTrue(a.gathering().active() && !a.gathering().hasCleanup(),
                "An unreachable temporary pillar permanently blocks gathering");
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(support)).isOf(Blocks.COBBLESTONE),
                "Cleanup mined the block under the NPC");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=20)
    public void lostBenchRemainsRecordedForRecovery(TestContext c){
        CompanionEntity a=worker(c);
        BlockPos remote=c.getAbsolutePos(new BlockPos(7,2,7));
        c.getWorld().setBlockState(remote,Blocks.CRAFTING_TABLE.getDefaultState());
        NbtCompound state=new NbtCompound();
        state.putLongArray("DeferredOwnedBenches",new long[]{remote.asLong()});
        a.crafting().readNbt(state);
        NbtCompound persisted=new NbtCompound();a.crafting().writeNbt(persisted);
        c.assertTrue(persisted.getLongArray("DeferredOwnedBenches").length==1
            && persisted.getLongArray("DeferredOwnedBenches")[0]==remote.asLong(),
            "Recovery memory lost bench ownership after reload");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="pillar_recovery",tickLimit=720)
    public void tallWoodTowerDescendsAndPhysicallyRecoversEverySupport(TestContext c){
        CompanionEntity a=worker(c);
        // Keep real gravity/travel enabled; WAIT isolates the direct executor.
        a.setMode(Mode.WAIT);
        a.autoLoot=true;
        BlockPos column=new BlockPos(4,2,4);
        NbtList saved=new NbtList();
        for(int dy=0;dy<3;dy++){
            BlockPos pos=column.up(dy);
            c.setBlockState(pos,Blocks.SPRUCE_LOG);
            OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(pos),a.getUuid());
            NbtCompound support=new NbtCompound();
            support.putLong("Pos",c.getAbsolutePos(pos).asLong());
            support.putString("Block","minecraft:spruce_log");
            saved.add(support);
        }
        a.refreshPositionAndAngles(c.getAbsolutePos(new BlockPos(4,5,4)).getX()+0.5,
            c.getAbsolutePos(new BlockPos(4,5,4)).getY(),
            c.getAbsolutePos(new BlockPos(4,5,4)).getZ()+0.5,0,0);
        a.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,6));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        NbtCompound nbt=new NbtCompound();nbt.put("TemporaryPillar",saved);
        TemporaryPillar tower=new TemporaryPillar(a);tower.readNbt(nbt);
        PhysicalMining mining=new PhysicalMining(a);
        for(int tick=15;tick<=620;tick++) c.runAtTick(tick,()->tower.cleanup(mining));
        c.runAtTick(650,()->{
            c.assertFalse(tower.active(),"The tower has not been physically dismantled");
            for(int dy=0;dy<3;dy++) c.assertTrue(
                c.getWorld().getBlockState(c.getAbsolutePos(column.up(dy))).isAir(),
                "A temporary spruce support is still in the tower");
            int dropped=c.getWorld().getEntitiesByClass(ItemEntity.class,
                new Box(c.getAbsolutePos(column)).expand(9),
                item->item.isAlive() && item.getStack().isOf(Items.SPRUCE_LOG))
                .stream().mapToInt(item->item.getStack().getCount()).sum();
            c.assertTrue(a.backpack.count(Items.SPRUCE_LOG)+dropped==9,
                "Support materials were destroyed or duplicated during descent");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="pillar_recovery",tickLimit=1100)
    public void waitModeRecoversTallTowerAndCollectsAllDrops(TestContext c){
        recoverTallTower(c,1,false);
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="pillar_recovery",tickLimit=1100)
    public void tallTowerWithoutSpareMaterialRecoversEveryDrop(TestContext c){
        recoverTallTower(c,0,false);
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="pillar_recovery",tickLimit=1100)
    public void savedSideDescentResumesWithoutInventingMaterial(TestContext c){
        recoverTallTower(c,0,true);
    }
    private void recoverTallTower(TestContext c,int spare,boolean savedSide){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);a.autoLoot=true;
        BlockPos column=new BlockPos(4,2,4);NbtList supports=new NbtList();
        for(int dy=0;dy<6;dy++){
            BlockPos p=column.up(dy);c.setBlockState(p,Blocks.SPRUCE_LOG);
            OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(p),a.getUuid());
            NbtCompound n=new NbtCompound();n.putLong("Pos",c.getAbsolutePos(p).asLong());
            n.putString("Block","minecraft:spruce_log");supports.add(n);
        }
        Vec3d start=Vec3d.ofBottomCenter(c.getAbsolutePos(column.up(6)));
        a.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        a.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,spare));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        NbtCompound saved=new NbtCompound();saved.put("TemporaryPillar",supports);
        if(savedSide){
            BlockPos side=column.up(4).north();
            c.setBlockState(side,Blocks.SPRUCE_LOG);
            OwnedBlocks.get(c.getWorld()).claim(c.getAbsolutePos(side),a.getUuid());
            NbtCompound n=new NbtCompound();n.putLong("Pos",c.getAbsolutePos(side).asLong());
            n.putString("Block","minecraft:spruce_log");supports.add(n);
            saved.putString("PillarDescent","MINE_TOP");
            saved.putLong("PillarDescentSide",c.getAbsolutePos(side).asLong());
            saved.putLong("PillarDescentUpper",c.getAbsolutePos(column.up(5)).asLong());
            Vec3d onSide=Vec3d.ofBottomCenter(c.getAbsolutePos(side.up()));
            a.refreshPositionAndAngles(onSide.x,onSide.y,onSide.z,90,0);
        }
        a.gathering().readNbt(saved);
        c.runAtTick(1000,()->{
            c.assertFalse(a.gathering().hasCleanup(),"Cleanup remains pending: "+a.intelligence().status());
            for(int dy=0;dy<6;dy++)c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(column.up(dy))).isAir(),
                "Tower support remains at level "+dy+" npc="+a.getPos()
                    +" inventory="+a.backpack.count(Items.SPRUCE_LOG)+" "
                    +a.intelligence().status()+" | "+a.gathering().pillarDiagnostics());
            c.assertTrue(a.backpack.count(Items.SPRUCE_LOG)==6+spare+(savedSide?1:0),"Must physically collect every support, including the reusable side ledge: "+a.backpack.count(Items.SPRUCE_LOG)+" pos="+a.getPos()+" "+a.intelligence().status());
            c.assertTrue(a.isOnGround() && a.getY()==c.getAbsolutePos(column).getY(),"Did not return to ground");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=180)
    public void climbsWithOnePlankAndRecoversIt(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        a.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS));
        TemporaryPillar pillar=new TemporaryPillar(a);
        PhysicalMining mining=new PhysicalMining(a);
        BlockPos support=a.getBlockPos();
        for(int t=10;t<40;t++)c.runAtTick(t,()->{if(!pillar.owns(support))pillar.climb();});
        c.runAtTick(45,()->{
            c.assertTrue(pillar.owns(support),"Single real plank did not become a support");
            c.assertTrue(a.backpack.count(Items.OAK_PLANKS)==0,"Placed plank not consumed");
        });
        for(int t=46;t<160;t++)c.runAtTick(t,()->pillar.cleanup(mining));
        c.runAtTick(165,()->{
            c.assertFalse(pillar.active(),"Support not recovered");
            c.assertTrue(a.backpack.count(Items.OAK_PLANKS)==1,"Plank lost or duplicated");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="world_regression",tickLimit=1800)
    public void woodOrderClimbsToNaturalUpperTrunk(TestContext c){
        CompanionEntity a=worker(c);a.setMode(Mode.WAIT);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        a.backpack.setStack(0,new ItemStack(Items.DIRT,10));
        BlockPos base=new BlockPos(4,2,4);
        for(int y=0;y<10;y++)c.setBlockState(base.up(y),Blocks.SPRUCE_LOG);
        c.setBlockState(base.up(10),Blocks.SPRUCE_LEAVES);
        a.gathering().start(ResourceGathering.Kind.WOOD,10,true);
        c.runAtTick(1700,()->{
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(base.up(9))).isAir(),"Upper trunk was abandoned: "+a.gathering().pillarDiagnostics());
            c.assertTrue(a.backpack.count(Items.SPRUCE_LOG)==10,"Tree drops not physically recovered");
            c.assertTrue(a.backpack.count(Items.DIRT)==10 && !a.gathering().hasCleanup(),"Borrowed supports not fully recovered");
            c.complete();
        });
    }
}
