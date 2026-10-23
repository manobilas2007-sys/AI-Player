package dev.aiplayer.entity;

import dev.aiplayer.ai.*;
import dev.aiplayer.screen.CompanionMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.*;

public class CompanionEntity extends PathAwareEntity implements ExtendedScreenHandlerFactory {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> MODE = DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EATING = DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> WORKING = DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> ATTACK_TICKS = DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> INTERACTION=DataTracker.registerData(CompanionEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> INTERACTION_TICKS=DataTracker.registerData(CompanionEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private int lastWorkTick=-100;
    public static final int PLACE=1, CRAFT=2, EQUIP=3;
    public int interactionVisual(){return dataTracker.get(INTERACTION_TICKS)>0?dataTracker.get(INTERACTION):0;}
    public void animateInteraction(int action,int ticks){
        if(getWorld().isClient)return;
        if(action==EQUIP && (isWorkingVisual() || attackVisualTicks()>0 || isEating() || interactionVisual()==PLACE))return;
        dataTracker.set(INTERACTION,action);dataTracker.set(INTERACTION_TICKS,ticks);
    }
    public void clearActionVisual(){
        clearWorkingVisual();dataTracker.set(ATTACK_TICKS,0);dataTracker.set(INTERACTION_TICKS,0);
    }
    public static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
    public final SimpleInventory backpack = new SimpleInventory(27);
    public boolean autoEquip = true;
    public boolean autoSurvive = true;
    public boolean autoLoot = true;
    public boolean autoCraft = true;
    private final dev.aiplayer.ai.brain.CompanionIntelligence intelligence=new dev.aiplayer.ai.brain.CompanionIntelligence(this);
    public dev.aiplayer.ai.brain.CompanionIntelligence intelligence(){return intelligence;}
    public SurvivalController survival(){return survival;}
    private final CompanionSpeech speech=new CompanionSpeech(this);
    public CompanionSpeech speech(){return speech;}
    private final SurvivalController survival = new SurvivalController(this);
    private final ResourceGathering gathering = new ResourceGathering(this);
    private final StarterCrafting crafting = new StarterCrafting(this);
    private LivingEntity ownerMarkedTarget;
    private int ownerMarkExpires;
    private boolean retreating;
    private int viewers;
    private String ownerName = "";
    public CompanionEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type,world);
        setPersistent(); setStepHeight(1.0f);
        getNavigation().setCanSwim(true);
        setPathfindingPenalty(PathNodeType.LAVA,-1);
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE,-1);
        setPathfindingPenalty(PathNodeType.DANGER_FIRE,16);
        setPathfindingPenalty(PathNodeType.WATER,0);
        setCustomName(Text.literal("AI Player")); setCustomNameVisible(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot,1.0f);
    }
    public static DefaultAttributeContainer.Builder attributes() {
        return createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,30)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED,0.29)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE,1)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE,32);
    }
    @Override protected void initDataTracker() {
        super.initDataTracker(); dataTracker.startTracking(OWNER, Optional.empty()); dataTracker.startTracking(MODE,0); dataTracker.startTracking(EATING,false);dataTracker.startTracking(WORKING,false);dataTracker.startTracking(ATTACK_TICKS,0);dataTracker.startTracking(INTERACTION,0);dataTracker.startTracking(INTERACTION_TICKS,0);
    }
    @Override protected void initGoals() {
        goalSelector.add(0,new SwimGoal(this));
        goalSelector.add(1,new CompanionGoal(this));
        goalSelector.add(8,new LookAtEntityGoal(this,PlayerEntity.class,8));
        goalSelector.add(9,new LookAroundGoal(this));
    }
    public UUID ownerId() { return dataTracker.get(OWNER).orElse(null); }
    public boolean isOwner(PlayerEntity player) { return player.getUuid().equals(ownerId()); }
    public PlayerEntity owner() { return ownerId()==null?null:getWorld().getPlayerByUuid(ownerId()); }
    public Mode mode() { return Mode.fromId(dataTracker.get(MODE)); }
    public void setMode(Mode mode) { dataTracker.set(MODE,mode.ordinal()); setTarget(null); getNavigation().stop(); }
    public boolean menuOpen() { return viewers > 0; }
    public boolean isEating() { return dataTracker.get(EATING); }
    public void setEatingVisual(boolean eating) { dataTracker.set(EATING,eating); }
    public boolean isWorkingVisual() {return dataTracker.get(WORKING);}
    public void markWorking() {lastWorkTick=age;dataTracker.set(WORKING,true);}
    public void clearWorkingVisual() {lastWorkTick=-100;dataTracker.set(WORKING,false);}
    public int attackVisualTicks() {return dataTracker.get(ATTACK_TICKS);}
    public void cancelEating() { survival.cancelEating(); }
    public ResourceGathering gathering() { return gathering; }
    public StarterCrafting crafting() { return crafting; }
    public boolean isRetreating() { return retreating; }
    public void setRetreating(boolean value) { retreating=value; }
    public void markOwnerAttack(LivingEntity mob) {
        if(mob!=null && canTarget(mob)) { ownerMarkedTarget=mob;ownerMarkExpires=age+200;setTarget(mob); }
    }
    public LivingEntity getOwnerMarkedTarget() {
        if(ownerMarkedTarget==null || age>ownerMarkExpires || !ownerMarkedTarget.isAlive()) ownerMarkedTarget=null;
        return ownerMarkedTarget;
    }
    public void opened() { survival.cancelEating(); viewers++; getNavigation().stop(); }
    public void closed() { viewers=Math.max(0,viewers-1); }
    @Override public boolean canTarget(LivingEntity target) {
        if (target instanceof PlayerEntity || target instanceof CompanionEntity) return false;
        return super.canTarget(target);
    }
    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held=player.getStackInHand(hand);
        if(ownerId()==null && held.isOf(Items.STICK)) {
            if(!getWorld().isClient) {
                dataTracker.set(OWNER,Optional.of(player.getUuid())); ownerName=player.getName().getString();
                setCustomName(Text.literal("AI Player • "+ownerName)); setMode(Mode.GUARD);
                if(!player.getAbilities().creativeMode) held.decrement(1);
                getWorld().sendEntityStatus(this,(byte)7);
                player.sendMessage(Text.literal("Companheiro domesticado! Interaja novamente para abrir o menu."),true);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if(isOwner(player)) {
            if(!getWorld().isClient) player.openHandledScreen(this);
            return ActionResult.success(getWorld().isClient);
        }
        if(!getWorld().isClient) player.sendMessage(Text.literal(ownerId()==null?"Use um graveto para domesticar.":"Este companheiro pertence a "+ownerName+"."),true);
        return ActionResult.success(getWorld().isClient);
    }
    @Override public void tick() {
        if(!getWorld().isClient && attackVisualTicks()>0)dataTracker.set(ATTACK_TICKS,attackVisualTicks()-1);
        if(!getWorld().isClient && dataTracker.get(INTERACTION_TICKS)>0)dataTracker.set(INTERACTION_TICKS,dataTracker.get(INTERACTION_TICKS)-1);
        super.tick();
        if(!getWorld().isClient && isAlive()) {
            if(age-lastWorkTick>2 || getTarget()!=null || menuOpen() || isEating())dataTracker.set(WORKING,false);
            intelligence.tick();
        }
    }
    public void optimizeEquipment() {
        EntityGroup enemy=getTarget()==null?EntityGroup.DEFAULT:getTarget().getGroup();
        for(EquipmentSlot slot:GEAR) {
            if(slot==EquipmentSlot.OFFHAND) continue;
            ItemStack current=getEquippedStack(slot);
            if(EnchantmentHelper.hasBindingCurse(current)) continue;
            int best=-1; double score=EquipmentPolicy.score(current,slot,enemy);
            for(int i=0;i<backpack.size();i++) {
                ItemStack item=backpack.getStack(i);
                double candidate=EquipmentPolicy.score(item,slot,enemy);
                if(EquipmentPolicy.accepts(item,slot) && candidate>score && item.getCount()==1) { best=i;score=candidate; }
            }
            if(best>=0) {
                ItemStack selected=backpack.removeStack(best);
                backpack.setStack(best,current); equipStack(slot,selected);animateInteraction(EQUIP,5);
            }
        }
    }
    public void equipMiningTool(net.minecraft.block.BlockState state) {
        equipMiningTool(state,stack -> true);
    }
    public void equipMiningTool(net.minecraft.block.BlockState state,java.util.function.Predicate<ItemStack> allowed) {
        if(!autoEquip || isEating())return;
        // Logs require a serviceable axe, or bare hands during bootstrap.
        // A worn axe must never make a pickaxe win merely by speed scoring.
        if(state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
            var requested=allowed;
            allowed=stack -> requested.test(stack) && (stack.isEmpty()
                || (stack.getItem() instanceof AxeItem && !dev.aiplayer.ai.brain.ResourcePlan.worn(stack)));
            if(!dev.aiplayer.ai.brain.ResourcePlan.usable(this,AxeItem.class)
                    && !getMainHandStack().isEmpty() && backpack.canInsert(getMainHandStack())) {
                ItemStack old=getMainHandStack();
                equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                backpack.addStack(old);backpack.markDirty();
            }
        }
        ItemStack current=getMainHandStack();
        float bestSpeed=allowed.test(current)?current.getMiningSpeedMultiplier(state):-1;
        if(dev.aiplayer.ai.brain.ResourcePlan.worn(current))bestSpeed*=0.1f;
        int best=-1;
        for(int i=0;i<backpack.size();i++) {
            ItemStack stack=backpack.getStack(i);
            if(stack.getCount()!=1 || !(stack.getItem() instanceof ToolItem) || !allowed.test(stack))continue;
            float speed=stack.getMiningSpeedMultiplier(state);
            if(dev.aiplayer.ai.brain.ResourcePlan.worn(stack))speed*=0.1f;
            if(speed>bestSpeed) {best=i;bestSpeed=speed;}
        }
        if(best>=0) {
            ItemStack selected=backpack.removeStack(best);
            backpack.setStack(best,current);
            equipStack(EquipmentSlot.MAINHAND,selected);animateInteraction(EQUIP,5);
        }
    }
    @Override public boolean tryAttack(Entity target) {
        if(autoEquip && !isEating())optimizeEquipment();
        clearWorkingVisual();
        dataTracker.set(ATTACK_TICKS,8);
        swingHand(Hand.MAIN_HAND);
        boolean hit=super.tryAttack(target);
        if(hit && target instanceof LivingEntity living && !getMainHandStack().isEmpty())
            getMainHandStack().getItem().postHit(getMainHandStack(),living,this);
        return hit;
    }
    @Override public void damageArmor(DamageSource source,float amount) {
        if(source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_ARMOR)) return;
        for(EquipmentSlot slot:GEAR) if(slot.getType()==EquipmentSlot.Type.ARMOR) {
            ItemStack stack=getEquippedStack(slot);
            if(stack.getItem() instanceof ArmorItem && !(source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE) && stack.getItem().isFireproof()))
                stack.damage(Math.max(1,(int)(amount/4)),this,e->e.sendEquipmentBreakStatus(slot));
        }
    }
    @Override protected void dropInventory() {
        super.dropInventory();
        for(int i=0;i<backpack.size();i++) {
            ItemStack stack=backpack.removeStack(i);
            if(!EnchantmentHelper.hasVanishingCurse(stack)) dropStack(stack);
        }
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if(ownerId()!=null) nbt.putUuid("Owner",ownerId());
        nbt.putString("OwnerName",ownerName); nbt.putInt("Mode",mode().ordinal()); nbt.putBoolean("AutoEquip",autoEquip);
        nbt.putBoolean("AutoSurvive",autoSurvive); nbt.putBoolean("AutoLoot",autoLoot);
        nbt.putBoolean("AutoCraft",autoCraft); nbt.putBoolean("Retreating",retreating);
        NbtList items=new NbtList();
        for(int i=0;i<27;i++) if(!backpack.getStack(i).isEmpty()) {
            NbtCompound item=new NbtCompound(); item.putByte("Slot",(byte)i); backpack.getStack(i).writeNbt(item); items.add(item);
        }
        nbt.putString("WorkDimension",getWorld().getRegistryKey().getValue().toString());
        nbt.put("Backpack",items); gathering.writeNbt(nbt); crafting.writeNbt(nbt); intelligence.write(nbt);survival.writeNbt(nbt);speech.write(nbt);nbt.putInt("AIPlayerDataVersion",5);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(OWNER,nbt.containsUuid("Owner")?Optional.of(nbt.getUuid("Owner")):Optional.empty());
        ownerName=nbt.getString("OwnerName"); setMode(Mode.fromId(nbt.getInt("Mode")));
        autoEquip=!nbt.contains("AutoEquip") || nbt.getBoolean("AutoEquip");
        autoSurvive=!nbt.contains("AutoSurvive") || nbt.getBoolean("AutoSurvive");
        autoLoot=!nbt.contains("AutoLoot") || nbt.getBoolean("AutoLoot");
        autoCraft=!nbt.contains("AutoCraft") || nbt.getBoolean("AutoCraft");
        retreating=nbt.getBoolean("Retreating"); backpack.clear();
        NbtList list=nbt.getList("Backpack",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++) { NbtCompound item=list.getCompound(i);int slot=item.getByte("Slot")&255;
            if(slot<27) backpack.setStack(slot,ItemStack.fromNbt(item)); }
        if(!nbt.contains("WorkDimension") || nbt.getString("WorkDimension").equals(getWorld().getRegistryKey().getValue().toString())){
            gathering.readNbt(nbt);crafting.readNbt(nbt);
        } else {gathering.readNbt(new NbtCompound());crafting.readNbt(new NbtCompound());}
        intelligence.read(nbt);survival.readNbt(nbt);speech.read(nbt);
    }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player,PacketByteBuf buf) { buf.writeInt(getId()); }
    @Override public ScreenHandler createMenu(int syncId,PlayerInventory inv,PlayerEntity player) { return new CompanionMenu(syncId,inv,this); }
}
