package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.compat.FallingTreeBridge;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class FallingTreeTests implements FabricGameTest {
    private CompanionEntity worker(TestContext c) {
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity a=c.spawnEntity(AIPlayer.COMPANION,new BlockPos(2,2,2));
        NbtCompound owner=new NbtCompound();a.writeCustomDataToNbt(owner);
        owner.putUuid("Owner",java.util.UUID.randomUUID());a.readCustomDataFromNbt(owner);
        a.setMode(Mode.WAIT);a.autoSurvive=false;a.autoCraft=false;return a;
    }
    private BlockPos tree(TestContext c,CompanionEntity a) {
        BlockPos base=c.getAbsolutePos(new BlockPos(4,2,4));
        long[] logs=new long[7];
        for(int i=0;i<7;i++){logs[i]=base.up(i).asLong();c.getWorld().setBlockState(base.up(i),Blocks.OAK_LOG.getDefaultState());}
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)
            c.getWorld().setBlockState(base.add(x,7,z),Blocks.OAK_LEAVES.getDefaultState());
        NbtCompound root=new NbtCompound(),task=new NbtCompound();
        task.putString("Kind","WOOD");task.putInt("Desired",7);task.putBoolean("Manual",true);
        task.putInt("X",base.getX());task.putInt("Y",base.getY());task.putInt("Z",base.getZ());
        task.putLong("Tree",base.asLong());task.putLongArray("Logs",logs);root.put("GatherTask",task);
        a.gathering().readNbt(root);return base;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree")
    public void fallingTreeCreatesPhysicalDropsOnce(TestContext c) {
        CompanionEntity a=worker(c);a.autoLoot=false;
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        BlockPos base=tree(c,a);
        c.assertTrue(FallingTreeBridge.tryFell(a,base),"FallingTree adapter refused natural tree");
        for(int i=0;i<7;i++)c.assertTrue(c.getWorld().getBlockState(base.up(i)).isAir(),"Log remains");
        c.assertTrue(a.backpack.count(Items.OAK_LOG)==0,"Remote inventory insertion");
        int drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(base).stretch(0,8,0).expand(2),
            e->e.getStack().isOf(Items.OAK_LOG)).stream().mapToInt(e->e.getStack().getCount()).sum();
        c.assertTrue(drops==7,"Missing/duplicated logs: "+drops);
        c.assertTrue(a.getMainHandStack().getDamage()>0,"No axe durability cost");
        c.assertFalse(FallingTreeBridge.tryFell(a,base),"Repeated cut produced a second tree");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree")
    public void fallingTreePreservesProtectedUpperLog(TestContext c) {
        CompanionEntity a=worker(c);a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        BlockPos base=tree(c,a);OwnedBlocks.get(c.getWorld()).playerPlaced(c.getWorld(),base.up(5));
        c.assertFalse(FallingTreeBridge.tryFell(a,base),"Protected tree was batch-mined");
        for(int i=0;i<7;i++)c.assertTrue(c.getWorld().getBlockState(base.up(i)).isOf(Blocks.OAK_LOG),"Preflight changed world");
        c.assertTrue(a.getMainHandStack().getDamage()==0,"Failed preflight damaged tool");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree")
    public void wornAxeNeverSelectsPickForLogs(TestContext c) {
        CompanionEntity a=worker(c);
        ItemStack worn=new ItemStack(Items.WOODEN_AXE);worn.setDamage(worn.getMaxDamage()-2);
        a.equipStack(EquipmentSlot.MAINHAND,worn);a.backpack.setStack(0,new ItemStack(Items.IRON_PICKAXE));
        a.equipMiningTool(Blocks.OAK_LOG.getDefaultState());
        c.assertTrue(a.getMainHandStack().isEmpty(),"Pick or worn axe used for bootstrap logs");
        c.assertTrue(a.backpack.count(Items.WOODEN_AXE)==1,"Old axe deleted before replacement");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree",tickLimit=650)
    public void craftsReplacementBeforeRetiringWornAxe(TestContext c) {
        CompanionEntity a=worker(c);a.autoCraft=true;
        ItemStack worn=new ItemStack(Items.WOODEN_AXE);worn.setDamage(worn.getMaxDamage()-2);
        a.equipStack(EquipmentSlot.MAINHAND,worn);
        a.backpack.setStack(0,new ItemStack(Items.OAK_LOG,6));
        a.backpack.setStack(1,new ItemStack(Items.OAK_PLANKS,12));
        a.backpack.setStack(2,new ItemStack(Items.STICK,8));
        a.backpack.setStack(3,new ItemStack(Items.CRAFTING_TABLE));
        a.backpack.setStack(4,new ItemStack(Items.WOODEN_PICKAXE));
        tree(c,a);
        c.runAtTick(550,()->{
            c.assertTrue(ResourcePlan.usable(a,AxeItem.class),"Replacement axe not crafted");
            c.assertFalse(a.getMainHandStack().getItem() instanceof AxeItem && ResourcePlan.worn(a.getMainHandStack()),"Worn axe still equipped");
            for(int i=0;i<a.backpack.size();i++)c.assertFalse(a.backpack.getStack(i).getItem() instanceof AxeItem && ResourcePlan.worn(a.backpack.getStack(i)),"Old worn axe retained after replacement");
            c.complete();
        });
    }
    /** Regression from FCL: after the last FallingTree cut the WOOD order
     * is gone, but the automatic equipment requirement must survive. In
     * GUARD mode a faraway owner used to win FOLLOW (70) over CRAFT (65). */
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree",tickLimit=700)
    public void replacesBrokenAxeAfterWoodOrderWhileGuarding(TestContext c) {
        CompanionEntity a=worker(c);
        var owner=c.createMockCreativeServerPlayerInWorld();
        Vec3d away=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(22,2,2)));
        owner.refreshPositionAndAngles(away.x,away.y,away.z,0,0);
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);
        n.putUuid("Owner",owner.getUuid());a.readCustomDataFromNbt(n);
        a.setMode(Mode.GUARD);a.autoCraft=true;a.autoEquip=true;a.autoSurvive=false;
        a.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,63));
        a.backpack.setStack(1,new ItemStack(Items.STICK,11));
        a.backpack.setStack(2,new ItemStack(Items.CRAFTING_TABLE));
        a.backpack.setStack(3,new ItemStack(Items.WOODEN_PICKAXE));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));
        c.runAtTick(580,()->{
            c.assertTrue(ResourcePlan.usable(a,AxeItem.class),
                "Guard/follow starved replacement after finished wood task: "+a.intelligence().status()
                    +" bench="+a.crafting().diagnostics()+" logs="+a.backpack.count(Items.SPRUCE_LOG));
            owner.discard();c.complete();
        });
    }
    /** Shallow snow is replaceable when placing a real crafting table. */
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree",tickLimit=700)
    public void snowyGroundAllowsCraftingNewAxe(TestContext c) {
        CompanionEntity a=worker(c);a.autoCraft=true;a.autoEquip=true;
        for(net.minecraft.util.math.Direction d:net.minecraft.util.math.Direction.Type.HORIZONTAL)
            c.setBlockState(new BlockPos(2,2,2).offset(d),Blocks.SNOW);
        a.backpack.setStack(0,new ItemStack(Items.SPRUCE_LOG,12));
        a.backpack.setStack(1,new ItemStack(Items.STICK,8));
        a.backpack.setStack(2,new ItemStack(Items.CRAFTING_TABLE));
        ItemStack worn=new ItemStack(Items.WOODEN_AXE);
        worn.setDamage(worn.getMaxDamage()-2);
        a.equipStack(EquipmentSlot.MAINHAND,worn);
        c.runAtTick(580,()->{
            c.assertTrue(ResourcePlan.usable(a,AxeItem.class),
                "Snow blocked real workbench placement or axe renewal: "+a.intelligence().status()
                    +" bench="+a.crafting().diagnostics());
            c.complete();
        });
    }
    /** FCL regression: the table item was already recovered, but the old
     * reclamation session still existed when the axe became unusable. Missing
     * future stone upgrades must not block replacement during a WOOD order. */
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree",tickLimit=400)
    public void replacesWornAxeDuringWoodOrderAfterBenchPickup(TestContext c) {
        CompanionEntity a=worker(c);a.autoCraft=true;a.autoEquip=true;
        ItemStack worn=new ItemStack(Items.WOODEN_AXE);worn.setDamage(worn.getMaxDamage()-2);
        a.backpack.setStack(0,worn);
        a.backpack.setStack(1,new ItemStack(Items.STICK,11));
        a.backpack.setStack(2,new ItemStack(Items.WOODEN_PICKAXE));
        a.backpack.setStack(3,new ItemStack(Items.CRAFTING_TABLE));
        a.backpack.setStack(4,new ItemStack(Items.SPRUCE_LOG,55));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));
        BlockPos oldBench=c.getAbsolutePos(new BlockPos(3,2,2));
        c.getWorld().setBlockState(oldBench,Blocks.AIR.getDefaultState());
        NbtCompound saved=new NbtCompound();saved.putLong("OwnedBench",oldBench.asLong());
        a.crafting().readNbt(saved);
        c.assertTrue(a.crafting().isReclaiming(),"Fixture lacks pending bench reclamation");
        c.assertTrue(ResourcePlan.stoneNeeded(a)>0,"Fixture lacks future stone upgrades");
        BlockPos base=tree(c,a);
        NbtCompound task=a.gathering().taskSnapshot();
        task.getCompound("GatherTask").putInt("Desired",50);a.gathering().readNbt(task);
        c.runAtTick(20,()->{
            c.assertTrue(java.util.stream.IntStream.range(0,a.backpack.size()).anyMatch(i->a.backpack.getStack(i)==worn),"Worn axe discarded before replacement recipe");
            c.assertTrue(a.gathering().isManual() && a.gathering().goal()==50,
                "Axe maintenance lost manual wood quantity");
            c.assertTrue(a.gathering().progress()==0 && c.getWorld().getBlockState(base).isOf(Blocks.OAK_LOG),
                "Continued bare-handed wood mining before replacing axe");
        });
        c.runAtTick(240,()->{
            c.assertTrue(ResourcePlan.usable(a,AxeItem.class),
                "Bench recovery blocked axe renewal during wood order: "+a.intelligence().status()
                    +" crafting="+a.crafting().diagnostics());
            for(int i=0;i<a.backpack.size();i++)c.assertFalse(
                a.backpack.getStack(i).getItem() instanceof AxeItem && ResourcePlan.worn(a.backpack.getStack(i)),
                "Worn axe kept after real replacement");
            c.assertTrue(a.gathering().isManual() && a.gathering().goal()==50 && a.gathering().progress()>0,
                "Wood order did not resume with its original quantity: "+a.intelligence().status());
            c.complete();
        });
    }
    /** Same inventory as the FCL report, after WOOD has finished. An absent
     * owned table must finish its stale recovery even outside a cave. */
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="fallingtree",tickLimit=650)
    public void replacesWornAxeAfterWoodOrderAndBenchPickup(TestContext c) {
        CompanionEntity a=worker(c);a.autoCraft=true;a.autoEquip=true;
        var owner=c.createMockCreativeServerPlayerInWorld();
        Vec3d away=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(22,2,2)));
        owner.refreshPositionAndAngles(away.x,away.y,away.z,0,0);
        NbtCompound ownerData=new NbtCompound();a.writeCustomDataToNbt(ownerData);
        ownerData.putUuid("Owner",owner.getUuid());a.readCustomDataFromNbt(ownerData);
        a.setMode(Mode.GUARD);
        ItemStack worn=new ItemStack(Items.WOODEN_AXE);worn.setDamage(worn.getMaxDamage()-2);
        a.backpack.setStack(0,worn);
        a.backpack.setStack(1,new ItemStack(Items.STICK,11));
        a.backpack.setStack(2,new ItemStack(Items.WOODEN_PICKAXE));
        a.backpack.setStack(3,new ItemStack(Items.CRAFTING_TABLE));
        a.backpack.setStack(4,new ItemStack(Items.SPRUCE_LOG,55));
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));
        BlockPos oldBench=c.getAbsolutePos(new BlockPos(3,2,2));
        c.getWorld().setBlockState(oldBench,Blocks.AIR.getDefaultState());
        NbtCompound saved=new NbtCompound();saved.putLong("OwnedBench",oldBench.asLong());
        a.crafting().readNbt(saved);
        c.assertTrue(a.crafting().isReclaiming() && !a.gathering().active(),
            "Fixture lacks the pending post-order bench session");
        c.assertFalse(SurfaceStoneGathering.caveAccess(a,a.getBlockPos()),"Fixture is inside a cave");
        c.runAtTick(20,()->c.assertTrue(java.util.stream.IntStream.range(0,a.backpack.size()).anyMatch(i->a.backpack.getStack(i)==worn),
            "Worn axe discarded before replacement recipe"));
        c.runAtTick(550,()->{
            c.assertTrue(ResourcePlan.usable(a,AxeItem.class),
                "Finished wood order left a worn axe forever: "+a.intelligence().status()
                    +" crafting="+a.crafting().diagnostics());
            for(int i=0;i<a.backpack.size();i++)c.assertFalse(
                a.backpack.getStack(i).getItem() instanceof AxeItem && ResourcePlan.worn(a.backpack.getStack(i)),
                "Worn axe kept after real replacement");
            c.assertTrue(a.backpack.count(Items.SPRUCE_LOG)<55,"Replacement consumed no real wood");
            owner.discard();c.complete();
        });
    }

}
