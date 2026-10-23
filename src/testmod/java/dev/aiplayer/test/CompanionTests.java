package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import java.util.UUID;

public class CompanionTests implements FabricGameTest {
    private CompanionEntity npc(TestContext c) {return c.spawnEntity(AIPlayer.COMPANION,new BlockPos(2,2,2));}
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void persistence(TestContext c) {
        CompanionEntity a=npc(c);UUID id=UUID.randomUUID();
        NbtCompound initial=new NbtCompound();a.writeCustomDataToNbt(initial);initial.putUuid("Owner",id);
        a.readCustomDataFromNbt(initial);a.setMode(Mode.WAIT);a.autoEquip=false;
        ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamage(75);sword.addEnchantment(Enchantments.SHARPNESS,2);
        a.equipStack(EquipmentSlot.MAINHAND,sword);a.backpack.setStack(26,new ItemStack(Items.IRON_INGOT,37));
        a.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        NbtCompound saved=new NbtCompound();a.writeCustomDataToNbt(saved);
        CompanionEntity b=AIPlayer.COMPANION.create(c.getWorld());b.readCustomDataFromNbt(saved);
        c.assertTrue(id.equals(b.ownerId()),"Owner UUID lost");
        c.assertTrue(b.mode()==Mode.WAIT && !b.autoEquip,"Mode/options lost");
        c.assertTrue(b.backpack.getStack(26).isOf(Items.IRON_INGOT) && b.backpack.getStack(26).getCount()==37,"Backpack changed");
        c.assertTrue(b.getMainHandStack().getDamage()==75 && b.getMainHandStack().hasEnchantments(),"Durability/enchantments lost");
        c.assertTrue(b.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.IRON_CHESTPLATE),"Armor lost");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void equipmentConservesItems(TestContext c) {
        CompanionEntity a=npc(c);a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));
        a.backpack.setStack(0,new ItemStack(Items.DIAMOND_SWORD));
        a.backpack.setStack(1,new ItemStack(Items.DIAMOND_CHESTPLATE));
        for(int i=2;i<27;i++)a.backpack.setStack(i,new ItemStack(Items.COBBLESTONE,64));
        a.optimizeEquipment();a.optimizeEquipment();
        c.assertTrue(a.getMainHandStack().isOf(Items.DIAMOND_SWORD),"Weapon not equipped");
        c.assertTrue(a.backpack.getStack(0).isOf(Items.WOODEN_SWORD),"Old weapon lost or duplicate");
        c.assertTrue(a.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE),"Armor not equipped");
        c.assertTrue(a.backpack.getStack(1).isEmpty(),"Armor duplicated");
        int count=0;for(int i=0;i<27;i++)count+=a.backpack.getStack(i).getCount();
        c.assertTrue(count==25*64+1,"Inventory conservation failed");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=40)
    public void realCombatAndDurability(TestContext c) {
        CompanionEntity a=npc(c);a.autoEquip=false;a.setAiDisabled(true);
        var target=c.spawnEntity(EntityType.ZOMBIE,new BlockPos(3,2,2));target.setAiDisabled(true);target.setInvulnerable(false);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        a.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        c.runAtTick(3,()->{
            float before=target.getHealth();boolean hit=a.tryAttack(target);
            c.assertTrue(hit && target.getHealth()<before-4,"Real weapon damage missing");
            c.assertTrue(a.getMainHandStack().getDamage()==1,"Weapon durability not consumed");
            a.damage(c.getWorld().getDamageSources().mobAttack(target),6);
            c.assertTrue(a.getEquippedStack(EquipmentSlot.CHEST).getDamage()>0,"Armor durability not consumed");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=180)
    public void navigationClimbsBlock(TestContext c) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        for(int x=4;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,2,z),Blocks.STONE);
        CompanionEntity a=npc(c);
        BlockPos dest=c.getAbsolutePos(new BlockPos(6,3,2));
        c.runAtTick(5,()->a.getNavigation().startMovingTo(dest.getX()+0.5,dest.getY(),dest.getZ()+0.5,1.0));
        c.runAtTick(140,()->{
            c.assertTrue(a.getY()>=dest.getY()-0.1 && a.squaredDistanceTo(dest.getX()+0.5,dest.getY(),dest.getZ()+0.5)<4,"Did not climb one-block ledge");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void ownershipAndRealSlots(TestContext c) {
        CompanionEntity a=npc(c);
        var owner=c.createMockSurvivalPlayer();owner.setPosition(a.getPos());
        owner.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.STICK));
        a.interact(owner,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(a.isOwner(owner),"Taming failed");
        c.assertTrue(owner.getMainHandStack().isEmpty(),"Taming did not consume stick");
        var menu=new dev.aiplayer.screen.CompanionMenu(10,owner.getInventory(),a);
        a.backpack.setStack(0,new ItemStack(Items.DIAMOND,12));
        menu.quickMove(owner,0);
        c.assertTrue(a.backpack.getStack(0).isEmpty() && owner.getInventory().count(Items.DIAMOND)==12,"Real slot withdrawal failed");
        for(int i=33;i<69;i++)if(menu.slots.get(i).getStack().isOf(Items.DIAMOND))menu.quickMove(owner,i);
        c.assertTrue(a.backpack.count(Items.DIAMOND)==12 && owner.getInventory().count(Items.DIAMOND)==0,"Real slot deposit failed");
        var stranger=c.createMockCreativePlayer();stranger.setPosition(a.getPos());
        c.assertFalse(menu.canUse(stranger),"Non-owner accessed backpack");
        c.assertFalse(menu.onButtonClick(stranger,2),"Non-owner issued order");
        c.assertTrue(menu.onButtonClick(owner,2) && a.mode()==Mode.WAIT,"Owner order failed");
        menu.onClosed(owner);c.assertFalse(a.menuOpen(),"Menu lock leaked");c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=100)
    public void monsterRecognizesCompanion(TestContext c) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);
        var zombie=c.spawnEntity(EntityType.ZOMBIE,new BlockPos(5,2,2));
        zombie.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        c.runAtTick(60,()->{c.assertTrue(zombie.getTarget() instanceof CompanionEntity,"Zombie did not target companion");c.complete();});
    }

    @GameTest(templateName="aiplayer_test:surface_arena", tickLimit=95)
    public void consumesRealFoodAndRestoresWeapon(TestContext c) {
        CompanionEntity a=npc(c);
        NbtCompound data=new NbtCompound();a.writeCustomDataToNbt(data);
        data.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(data);
        a.autoCraft=false;a.autoEquip=false;a.setMode(Mode.WAIT);
        a.setHealth(10);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        a.backpack.setStack(0,new ItemStack(Items.COOKED_BEEF,3));
        c.runAtTick(30,()->{
            c.assertTrue(a.getHealth()>10,"Food did not heal companion");
            c.assertTrue(a.getMainHandStack().isOf(Items.IRON_SWORD),"Sword was not restored after eating");
            c.assertTrue(a.backpack.count(Items.COOKED_BEEF)==2,"Food duplicated or not consumed");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void ownerAttackTargetIsPrioritized(TestContext c) {
        CompanionEntity a=npc(c);
        var slime=c.spawnEntity(EntityType.SLIME,new BlockPos(4,2,2));
        a.markOwnerAttack(slime);
        c.assertTrue(a.getTarget()==slime,"Companion did not select owner's target");
        c.assertTrue(dev.aiplayer.ai.CompanionGoal.isNaturallyHostile(slime),"Slime is not recognized as hostile");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena", tickLimit=200)
    public void picksUpDroppedItemsWithoutCopies(TestContext c) {
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.DIRT);
        CompanionEntity a=npc(c);
        NbtCompound data=new NbtCompound();a.writeCustomDataToNbt(data);
        data.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(data);
        a.autoCraft=false;
        var item=new net.minecraft.entity.ItemEntity(c.getWorld(),a.getX(),a.getY(),a.getZ(),new ItemStack(Items.IRON_INGOT,5));
        c.getWorld().spawnEntity(item);
        c.runAtTick(80,()->{
            c.assertTrue(a.backpack.count(Items.IRON_INGOT)==5,"Drops were not collected into the backpack");
            c.assertTrue(!item.isAlive(),"Collected drop still exists (duplication)");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena", tickLimit=110)
    public void miningSpawnsLootInsteadOfRemoteInsertion(TestContext c) {
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);a.autoLoot=false;a.autoCraft=false;
        BlockPos relative=new BlockPos(3,2,2);
        c.setBlockState(relative,Blocks.OAK_LOG);
        BlockPos target=c.getAbsolutePos(relative);
        var mining=new dev.aiplayer.ai.PhysicalMining(a);
        for(int tick=5;tick<=80;tick++)c.runAtTick(tick,()->mining.tick(target));
        c.runAtTick(6,()->c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_LOG),"Bare-hand break was instant"));
        c.runAtTick(85,()->{
            c.assertTrue(c.getWorld().getBlockState(target).isAir(),"Timed mining failed: pos="+a.getPos()+" ground="+a.isOnGround()+" reachable="+mining.reachable(target)+" age="+a.age);
            c.assertTrue(a.backpack.count(Items.OAK_LOG)==0,"Loot teleported to inventory");
            int total=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(target).expand(3),e -> e.getStack().isOf(Items.OAK_LOG))
                .stream().mapToInt(e -> e.getStack().getCount()).sum();
            c.assertTrue(total==1,"Expected exactly one physical log drop");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena", tickLimit=40)
    public void pickupCountsWoodAfterInventoryTransfer(TestContext c) {
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);a.setAiDisabled(true);a.autoCraft=false;a.autoEquip=false;
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,8,false);
        var drop=new net.minecraft.entity.ItemEntity(c.getWorld(),a.getX(),a.getY(),a.getZ(),new ItemStack(Items.OAK_LOG,3));
        drop.setPickupDelay(0);c.getWorld().spawnEntity(drop);
        c.runAtTick(25,()->{
            c.assertTrue(a.backpack.count(Items.OAK_LOG)==3,"Pickup lost items");
            c.assertTrue(a.gathering().progress()==3,"Pickup lost item identity before counting");
            c.assertTrue(!drop.isAlive(),"Pickup duplicated items");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void reloadPreservesBenchWithoutValidRecipe(TestContext c) {
        CompanionEntity a=npc(c);
        BlockPos bench=c.getAbsolutePos(new BlockPos(3,2,2));
        NbtCompound saved=new NbtCompound();saved.putLong("OwnedBench",bench.asLong());
        NbtCompound brokenRecipe=new NbtCompound();brokenRecipe.putString("Result","broken recipe");
        saved.put("CraftSession",brokenRecipe);
        a.crafting().readNbt(saved);
        c.assertTrue(a.crafting().isWorking(),"Bench recovery lost on invalid saved recipe");
        NbtCompound again=new NbtCompound();a.crafting().writeNbt(again);
        c.assertTrue(again.contains("OwnedBench") && again.getLong("OwnedBench")==bench.asLong(),"Bench identity lost");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void plannerFindsMissingMaterialsAtAnyTick(TestContext c) {
        CompanionEntity a=npc(c);a.age=37;
        var plan=dev.aiplayer.ai.brain.ResourcePlan.inspect(a);
        c.assertTrue(plan.toolsNeeded() && plan.missingLogs()>0,"Planner failed to identify missing starter resources");
        a.backpack.setStack(0,new ItemStack(Items.WOODEN_AXE));
        a.backpack.setStack(1,new ItemStack(Items.WOODEN_PICKAXE));
        a.backpack.setStack(2,new ItemStack(Items.WOODEN_SWORD));
        a.backpack.setStack(3,new ItemStack(Items.OAK_LOG,8));
        c.assertFalse(dev.aiplayer.ai.brain.ResourcePlan.inspect(a).toolsNeeded(),"Planner ignored real tools");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena", tickLimit=500)
    public void craftingKeepsBenchAcrossRecipes(TestContext c) {
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        a.autoSurvive=false;a.autoEquip=false;
        a.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,32));
        a.backpack.setStack(1,new ItemStack(Items.STICK,16));
        a.backpack.setStack(2,new ItemStack(Items.CRAFTING_TABLE));
        final long[] bench={0};
        c.runAtTick(75,()->{
            NbtCompound state=new NbtCompound();a.crafting().writeNbt(state);
            c.assertTrue(state.contains("OwnedBench"),"Bench removed after first recipe");bench[0]=state.getLong("OwnedBench");
            c.assertTrue(a.backpack.count(Items.WOODEN_PICKAXE)==1,"First tool missing");
            c.assertFalse(a.isWorkingVisual(),"Crafting incorrectly plays block-breaking animation");
        });
        c.runAtTick(115,()->{
            NbtCompound state=new NbtCompound();a.crafting().writeNbt(state);
            c.assertTrue(state.contains("OwnedBench") && state.getLong("OwnedBench")==bench[0],"Bench not reused for second recipe");
            c.assertTrue(a.backpack.count(Items.WOODEN_AXE)==1,"Second tool missing");
            c.assertTrue(c.getWorld().getBlockState(BlockPos.fromLong(bench[0])).isOf(Blocks.CRAFTING_TABLE),"Bench is not physically present");
        });
        c.runAtTick(450,()->{
            c.assertTrue(a.backpack.count(Items.WOODEN_SWORD)==1,"Last tool missing");
            c.assertTrue(a.backpack.count(Items.CRAFTING_TABLE)==1,"Bench not physically recovered: pos="+a.getPos()+" ground="+a.isOnGround()+" status="+a.intelligence().status()+" drops="+c.getWorld().getEntitiesByClass(ItemEntity.class,a.getBoundingBox().expand(8),e->true).stream().map(e->e.getStack()+"@"+e.getPos()).toList());c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void combatInterruptsAxeWithSwordAndRestoresMiningTool(TestContext c) {
        CompanionEntity a=npc(c);a.setAiDisabled(true);
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        a.backpack.setStack(0,new ItemStack(Items.IRON_SWORD));
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,8,false);
        var target=c.spawnEntity(EntityType.ZOMBIE,new BlockPos(3,2,2));
        target.setAiDisabled(true);
        a.markWorking();a.tryAttack(target);
        c.assertTrue(a.getMainHandStack().isOf(Items.IRON_SWORD),"Active wood task kept axe in combat");
        c.assertTrue(!a.isWorkingVisual() && a.attackVisualTicks()>0,"Attack did not replace mining animation");
        c.assertTrue(a.backpack.getStack(0).isOf(Items.IRON_AXE),"Axe lost during combat switch");
        a.equipMiningTool(Blocks.OAK_LOG.getDefaultState());
        c.assertTrue(a.getMainHandStack().isOf(Items.IRON_AXE),"Axe not restored for wood");
        c.assertTrue(a.backpack.getStack(0).isOf(Items.IRON_SWORD),"Sword lost during mining switch");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=180)
    public void foodTaskCollectsLooseWood(TestContext c) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);
        n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        a.autoCraft=false;a.autoSurvive=false;
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.FOOD,3,false);
        BlockPos pos=c.getAbsolutePos(new BlockPos(5,2,2));
        var drop=new ItemEntity(c.getWorld(),pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,new ItemStack(Items.SPRUCE_LOG,3));
        drop.setPickupDelay(0);c.getWorld().spawnEntity(drop);
        c.runAtTick(150,()->{
            c.assertTrue(a.backpack.count(Items.SPRUCE_LOG)==3 && !drop.isAlive(),"Loose wood ignored or duplicated while foraging: pos="+a.getPos()+" drop="+drop.getPos()+" alive="+drop.isAlive()+" count="+a.backpack.count(Items.SPRUCE_LOG)+" status="+a.intelligence().status()+" aiDisabled="+a.isAiDisabled()+" ground="+a.isOnGround()+" nav="+a.getNavigation().isIdle()+" speed="+a.getMovementSpeed());
            c.assertTrue(a.gathering().progress()==0,"Wood incorrectly counted as food");
            c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void emptyFoodSearchReleasesMovement(TestContext c) {
        CompanionEntity a=npc(c);a.setAiDisabled(true);a.autoCraft=false;
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.FOOD,3,false);
        c.assertFalse(a.gathering().tick(),"Empty food scan monopolized movement");
        c.assertFalse(a.gathering().tick(),"Food scan cooldown monopolized movement");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void autonomousTasksYieldButManualJobsStay(TestContext c) {
        CompanionEntity a=npc(c);a.setAiDisabled(true);
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,8,false);
        c.assertTrue(a.gathering().canYieldToOwner(),"Autonomous collection cannot yield to distant owner");
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,8,true);
        c.assertFalse(a.gathering().canYieldToOwner(),"Manual collection would be cancelled by owner distance");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void blockedBenchYieldsWithoutLosingOwnership(TestContext c) {
        CompanionEntity a=npc(c);a.setAiDisabled(true);
        NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);
        n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        BlockPos bench=c.getAbsolutePos(new BlockPos(7,2,7));
        c.getWorld().setBlockState(bench,Blocks.CRAFTING_TABLE.getDefaultState());
        NbtCompound state=new NbtCompound();state.putLong("OwnedBench",bench.asLong());
        a.crafting().readNbt(state);
        for(int tick=0;tick<80;tick++){a.age++;a.crafting().tick();}
        c.assertFalse(a.crafting().isWorking(),"Unreachable bench blocked movement indefinitely");
        NbtCompound saved=new NbtCompound();a.crafting().writeNbt(saved);
        c.assertTrue(saved.contains("OwnedBench") && saved.getLong("OwnedBench")==bench.asLong(),"Deferred recovery forgot bench");
        c.complete();
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void memoryPersistsAndChangesRouting(TestContext c) {
        var memory=new dev.aiplayer.ai.brain.BrainMemory();
        BlockPos a=new BlockPos(2,2,2),b=new BlockPos(3,2,2);
        memory.dimension("minecraft:overworld");
        memory.remember("tree",a,100,1000);memory.remember("tree",b,100,1000);
        memory.failed(a,100);
        c.assertTrue(memory.nearest("tree",a,101,10).orElseThrow().equals(b),"Failed route was selected again");
        NbtCompound data=new NbtCompound();memory.write(data);
        var loaded=new dev.aiplayer.ai.brain.BrainMemory();loaded.read(data);
        c.assertTrue(loaded.blocked(a,150),"Failure cooldown lost after save");
        c.assertFalse(loaded.blocked(a,250),"Failure never expired");
        loaded.dimension("minecraft:the_nether");
        c.assertTrue(loaded.nearest("tree",a,250,10).isEmpty(),"Cross-dimension resource leak");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void pausedGatheringSurvivesLongInterruption(TestContext c) {
        CompanionEntity a=npc(c);a.autoCraft=false;a.autoSurvive=false;
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,12,true);
        a.gathering().onCollected(new ItemStack(Items.OAK_LOG,3));
        a.gathering().pause();a.age+=1200;a.gathering().resume();
        a.gathering().tick();
        c.assertTrue(a.gathering().active() && a.gathering().progress()==3 && a.gathering().goal()==12,
            "Interruption expired the work or lost progress");
        NbtCompound state=new NbtCompound();a.gathering().writeNbt(state);
        CompanionEntity b=npc(c);b.gathering().readNbt(state);
        c.assertTrue(b.gathering().progress()==3 && b.gathering().goal()==12,"Resumable task lost on reload");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void mealReloadConservesFoodAndWeapon(TestContext c) {
        CompanionEntity a=npc(c);a.autoEquip=false;
        a.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        a.backpack.setStack(0,new ItemStack(Items.COOKED_BEEF,3));
        a.survival().requestMeal();
        NbtCompound data=new NbtCompound();a.writeCustomDataToNbt(data);
        CompanionEntity b=npc(c);b.readCustomDataFromNbt(data);b.cancelEating();
        c.assertTrue(b.getMainHandStack().isOf(Items.IRON_SWORD),"Reload lost swapped weapon");
        c.assertTrue(b.backpack.count(Items.COOKED_BEEF)==3,"Reload consumed/duplicated food without finishing");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=110)
    public void fullInventoryPreventsDestructiveMining(TestContext c) {
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);a.setAiDisabled(true);a.autoCraft=false;
        for(int i=0;i<27;i++)a.backpack.setStack(i,new ItemStack(Items.COBBLESTONE,64));
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,2));c.getWorld().setBlockState(p,Blocks.OAK_LOG.getDefaultState());
        var mining=new dev.aiplayer.ai.PhysicalMining(a);
        for(int t=5;t<90;t++)c.runAtTick(t,()->mining.tick(p));
        c.runAtTick(95,()->{
            c.assertTrue(c.getWorld().getBlockState(p).isOf(Blocks.OAK_LOG),"Destroyed resource without room for loot");
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==27*64,"Capacity simulation mutated inventory");
            c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void playerReplacementRevokesTemporaryOwnership(TestContext c) {
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,2));
        var registry=dev.aiplayer.ai.OwnedBlocks.get(c.getWorld());UUID id=UUID.randomUUID();
        c.getWorld().setBlockState(pos,Blocks.OAK_LOG.getDefaultState());registry.claim(pos,id);
        c.assertTrue(registry.owned(pos,id),"Claim not registered");
        registry.playerPlaced(c.getWorld(),pos);
        c.assertTrue(registry.protectedBlock(pos) && !registry.owned(pos,id),"Player replacement still owned by NPC");
        var copy=dev.aiplayer.ai.OwnedBlocks.read(registry.writeNbt(new NbtCompound()));
        c.assertTrue(copy.protectedBlock(pos),"Player construction protection lost after save");c.complete();
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void coordinatorPreemptsAndResumesRealJob(TestContext c) {
        CompanionEntity a=npc(c);a.autoCraft=false;a.autoSurvive=false;a.setAiDisabled(true);
        NbtCompound data=new NbtCompound();a.writeCustomDataToNbt(data);data.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(data);
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,12,true);
        a.gathering().onCollected(new ItemStack(Items.OAK_LOG,2));
        var zombie=c.spawnEntity(EntityType.ZOMBIE,new BlockPos(4,2,2));zombie.setAiDisabled(true);
        a.setTarget(zombie);a.age=1;a.intelligence().tick();
        c.assertTrue(a.intelligence().activity()==dev.aiplayer.ai.brain.DecisionPolicy.Activity.COMBAT,"Threat did not preempt collection");
        c.assertTrue(a.gathering().progress()==2 && a.gathering().goal()==12,"Combat erased job");
        zombie.discard();a.age=1001;a.intelligence().tick();
        c.assertTrue(a.intelligence().activity()==dev.aiplayer.ai.brain.DecisionPolicy.Activity.GATHER,"Work was not resumed");
        c.assertTrue(a.gathering().active() && a.gathering().progress()==2,"Long combat consumed task timeout");
        c.complete();
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void wornToolAndSupportReserveArePlanned(TestContext c) {
        CompanionEntity a=npc(c);a.setAiDisabled(true);
        ItemStack axe=new ItemStack(Items.IRON_AXE);axe.setDamage(axe.getMaxDamage()-2);
        a.backpack.setStack(0,axe);
        c.assertTrue(dev.aiplayer.ai.brain.ResourcePlan.inspect(a).toolsNeeded(),"Near-broken axe treated as permanent");
        a.backpack.setStack(1,new ItemStack(Items.OAK_LOG,2));
        c.assertTrue(dev.aiplayer.ai.brain.ResourcePlan.buildingSlot(a)==-1,"Pillar consumed last emergency logs");
        a.backpack.setStack(2,new ItemStack(Items.DIRT,8));
        c.assertTrue(dev.aiplayer.ai.brain.ResourcePlan.buildingSlot(a)==2,"Expendable building material not selected");
        c.complete();
    }

    private CompanionEntity workingNpc(TestContext c){
        CompanionEntity a=npc(c);NbtCompound n=new NbtCompound();a.writeCustomDataToNbt(n);
        n.putUuid("Owner",UUID.randomUUID());a.readCustomDataFromNbt(n);
        a.autoCraft=false;a.autoSurvive=false;return a;
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=500)
    public void consecutiveLogsWithDropsDoNotResetMining(TestContext c){
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.GRASS_BLOCK);
        for(int y=2;y<=4;y++)c.setBlockState(new BlockPos(4,y,2),Blocks.OAK_LOG);
        c.setBlockState(new BlockPos(4,5,2),Blocks.OAK_LEAVES);
        CompanionEntity a=workingNpc(c);
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,3,true);
        c.runAtTick(450,()->{
            for(int y=2;y<=4;y++)c.assertFalse(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(4,y,2))).isOf(Blocks.OAK_LOG),"Consecutive log stuck: "+y);
            c.assertTrue(a.backpack.count(Items.OAK_LOG)==3,"Physical tree drops not recovered exactly once: "+a.backpack.count(Items.OAK_LOG));c.complete();
        });
    }
    private void pit(TestContext c){
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)for(int y=1;y<=3;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.DIRT);
        c.setBlockState(new BlockPos(2,2,2),Blocks.AIR);c.setBlockState(new BlockPos(2,3,2),Blocks.AIR);
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=400)
    public void escapesPitByPhysicalStaircase(TestContext c){
        pit(c);CompanionEntity a=workingNpc(c);
        c.runAtTick(350,()->{c.assertTrue(a.getY()>=c.getAbsolutePos(new BlockPos(2,4,2)).getY(),"Failed to dig and climb a real step: "+a.getPos());c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=160)
    public void escapesPitWithInventorySupport(TestContext c){
        pit(c);CompanionEntity a=workingNpc(c);a.backpack.setStack(0,new ItemStack(Items.COBBLESTONE,3));
        c.runAtTick(120,()->{
            c.assertTrue(a.getY()>=c.getAbsolutePos(new BlockPos(2,4,2)).getY(),"Failed to climb support");
            BlockPos base=c.getAbsolutePos(new BlockPos(2,2,2));
            int placed=c.getWorld().getBlockState(base).isOf(Blocks.COBBLESTONE)?1:0;
            int drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new net.minecraft.util.math.Box(base).expand(5),e->e.getStack().isOf(Items.COBBLESTONE)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)+placed+drops==3,"Support material lost or duplicated");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void surfaceRouteRejectsCaveDetour(TestContext c){
        CompanionEntity a=npc(c);BlockPos p=a.getBlockPos();
        var nodes=java.util.List.of(new net.minecraft.entity.ai.pathing.PathNode(p.getX(),p.getY(),p.getZ()),
            new net.minecraft.entity.ai.pathing.PathNode(p.getX()+1,p.getY()-2,p.getZ()),
            new net.minecraft.entity.ai.pathing.PathNode(p.getX()+2,p.getY(),p.getZ()));
        var path=new net.minecraft.entity.ai.pathing.Path(nodes,p.east(2),true);
        c.assertFalse(dev.aiplayer.ai.WorkNavigation.safePath(a,path,p.getY()-1),"Surface work accepted a subterranean shortcut");c.complete();
    }

    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=500)
    public void escapesWideSnowyPitDuringManualWaitOrder(TestContext c){
        pit(c);
        for(int x=2;x<=3;x++)for(int z=2;z<=3;z++)for(int y=2;y<=3;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.AIR);
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)if(x<2||x>3||z<2||z>3)c.setBlockState(new BlockPos(x,4,z),Blocks.SNOW);
        CompanionEntity a=workingNpc(c);a.setMode(Mode.WAIT);
        a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,2,true);
        c.runAtTick(450,()->{c.assertTrue(a.getY()>=c.getAbsolutePos(new BlockPos(2,4,2)).getY(),"Wide snowy pit escape failed: "+a.getPos());c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=100)
    public void detectsTreeThirtyBlocksAway(TestContext c){
        for(int x=0;x<35;x++)for(int z=0;z<5;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.GRASS_BLOCK);
        BlockPos tree=new BlockPos(32,2,2);
        c.setBlockState(tree.down(),Blocks.GRASS_BLOCK);
        for(int y=0;y<4;y++)c.setBlockState(tree.up(y),Blocks.SPRUCE_LOG);
        c.setBlockState(tree.up(4),Blocks.SPRUCE_LEAVES);
        CompanionEntity a=workingNpc(c);a.gathering().start(dev.aiplayer.ai.ResourceGathering.Kind.WOOD,2,true);
        c.runAtTick(70,()->{
            c.assertTrue(a.intelligence().memory().nearest("tree",c.getAbsolutePos(tree),c.getWorld().getTime(),1).isPresent(),"Tree at thirty blocks was not detected: "+a.getPos()+" status="+a.intelligence().status()+" search="+a.gathering().searchStatus()+" loaded="+c.getWorld().isChunkLoaded(c.getAbsolutePos(tree))+" block="+c.getWorld().getBlockState(c.getAbsolutePos(tree))+" soil="+c.getWorld().getBlockState(c.getAbsolutePos(tree.down()))+" leaf="+c.getWorld().getBlockState(c.getAbsolutePos(tree.up(4))));c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena")
    public void followingAcceptsOpenSlope(TestContext c){
        CompanionEntity a=npc(c);BlockPos p=a.getBlockPos();
        // The new path safety rules require ACTUAL support blocks. A list of
        // elevated PathNodes floating above the EMPTY_STRUCTURE is not a slope.
        // Build a broad, continuously supported, two-step hillside instead;
        // this preserves the exact three-node uphill route under test.
        for(int x=-2;x<=4;x++){
            int rise=Math.max(0,Math.min(2,x));
            for(int z=-2;z<=2;z++)c.getWorld().setBlockState(p.add(x,rise-1,z),Blocks.STONE.getDefaultState());
        }
        var nodes=java.util.List.of(new net.minecraft.entity.ai.pathing.PathNode(p.getX(),p.getY(),p.getZ()),
            new net.minecraft.entity.ai.pathing.PathNode(p.getX()+1,p.getY()+1,p.getZ()),
            new net.minecraft.entity.ai.pathing.PathNode(p.getX()+2,p.getY()+2,p.getZ()));
        var path=new net.minecraft.entity.ai.pathing.Path(nodes,p.east(2).up(2),true);
        c.assertTrue(dev.aiplayer.ai.WorkNavigation.surfacePath(a,path,p.getY()+2),"Supported uphill path rejected");c.complete();
    }

    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=500)
    public void exitsPitSidewaysUnderWoodenRoof(TestContext c){
        pit(c);
        c.setBlockState(new BlockPos(3,2,2),Blocks.AIR);c.setBlockState(new BlockPos(3,3,2),Blocks.AIR);
        BlockPos roof=new BlockPos(2,4,2);c.setBlockState(roof,Blocks.OAK_PLANKS);
        CompanionEntity a=workingNpc(c);a.backpack.setStack(0,new ItemStack(Items.COBBLESTONE,3));
        c.runAtTick(400,()->{
            c.assertTrue(a.getY()>=c.getAbsolutePos(new BlockPos(2,4,2)).getY(),"Could not escape roofed pit: "+a.getPos());
            c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(roof)).isOf(Blocks.OAK_PLANKS),"Destroyed roof");
            c.assertFalse(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(2,2,2))).isOf(Blocks.COBBLESTONE),"Placed support under low roof");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",tickLimit=30)
    public void pillarChecksFullJumpHeight(TestContext c){
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        CompanionEntity a=npc(c);a.backpack.setStack(0,new ItemStack(Items.COBBLESTONE,3));
        c.setBlockState(new BlockPos(2,5,2),Blocks.OAK_PLANKS);
        c.runAtTick(10,()->{
            var pillar=new dev.aiplayer.ai.TemporaryPillar(a);
            c.assertFalse(pillar.climb(),"Jump accepted with ceiling intersecting apex");
            c.assertTrue(a.backpack.count(Items.COBBLESTONE)==3,"Consumed block despite roof");c.complete();
        });
    }

    @GameTest(templateName="aiplayer_test:surface_arena")
    public void speechThrottlesAndPersistsWithoutChangingInventory(TestContext c){
        CompanionEntity a=npc(c);a.backpack.setStack(0,new ItemStack(Items.OAK_LOG,4));
        var voice=a.speech();var event=dev.aiplayer.ai.CompanionSpeech.Event.FULL;
        String first=voice.line(event,1000);
        c.assertTrue(first!=null,"First diagnostic missing");
        c.assertTrue(voice.line(dev.aiplayer.ai.CompanionSpeech.Event.ROUTE,1001)==null,"Global speech spam guard failed");
        c.assertTrue(voice.line(event,1160)==null,"Repeated reason was not throttled");
        NbtCompound saved=new NbtCompound();voice.write(saved);
        var restored=new dev.aiplayer.ai.CompanionSpeech(a);restored.read(saved);
        c.assertTrue(restored.line(event,1500)==null,"Reload reset speech cooldown");
        String second=restored.line(event,2200);
        c.assertTrue(second!=null && !second.equals(first),"Phrase variation not retained");
        restored.enabled(false);restored.write(saved);
        var muted=new dev.aiplayer.ai.CompanionSpeech(a);muted.read(saved);
        c.assertFalse(muted.enabled(),"Mute preference lost");
        c.assertTrue(muted.line(event,5000)==null,"Muted NPC still talks automatically");
        c.assertTrue(a.backpack.count(Items.OAK_LOG)==4 && !a.gathering().active(),"Speech changed gameplay state");c.complete();
    }
}
