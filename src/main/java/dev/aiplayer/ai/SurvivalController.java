package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Server-side survival actions. Transfers actual stacks; never creates food. */
public final class SurvivalController {
    private final CompanionEntity npc;
    private int eatingTicks, nextMealAt;
    private int foodSlot = -1;

    public SurvivalController(CompanionEntity npc) { this.npc = npc; }
    public boolean isEating() { return foodSlot >= 0; }

    public void tick() {
        if (npc.getWorld().isClient || !npc.isAlive() || npc.ownerId() == null) return;
        if (isEating()) {
            if (npc.getAttacker() != null && npc.age - npc.getLastAttackedTime() < 15) {
                cancelEating();
            } else if (--eatingTicks <= 0) {
                finishEating();
            }
            return;
        }
        if (npc.autoLoot && npc.age % 10 == 0) collectNearby();
    }
    public boolean hasFood() {
        if(npc.age<nextMealAt)return false;
        for(int i=0;i<npc.backpack.size();i++)if(edible(npc.backpack.getStack(i)))return true;
        return false;
    }
    public void requestMeal(){if(!isEating() && !npc.menuOpen() && hasFood())startEating();}
    public void writeNbt(net.minecraft.nbt.NbtCompound root) {
        if(foodSlot>=0){root.putInt("MealSlot",foodSlot);root.putInt("MealTicks",eatingTicks);}
    }
    public void readNbt(net.minecraft.nbt.NbtCompound root) {
        foodSlot=-1;eatingTicks=0;npc.setEatingVisual(false);
        if(root.contains("MealSlot")) {
            int slot=root.getInt("MealSlot");
            if(slot>=0 && slot<npc.backpack.size()){
                foodSlot=slot;eatingTicks=Math.max(1,Math.min(24,root.getInt("MealTicks")));
                if(edible(npc.getMainHandStack()))npc.setEatingVisual(true);else cancelEating();
            }
        }
    }

    private boolean edible(ItemStack stack) {
        if (stack.isEmpty() || !stack.isFood()) return false;
        if (stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE)
                || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.PUFFERFISH)
                || stack.isOf(Items.CHICKEN)) return false;
        return stack.getItem().getFoodComponent() != null;
    }

    private void startEating() {
        int bestSlot = -1, bestValue = -1;
        for (int i = 0; i < npc.backpack.size(); i++) {
            ItemStack stack = npc.backpack.getStack(i);
            if (!edible(stack)) continue;
            FoodComponent food = stack.getItem().getFoodComponent();
            if (food != null && food.getHunger() > bestValue) {
                bestSlot = i; bestValue = food.getHunger();
            }
        }
        if (bestSlot < 0) return;
        ItemStack food = npc.backpack.removeStack(bestSlot);
        ItemStack originalWeapon = npc.getMainHandStack();
        npc.backpack.setStack(bestSlot, originalWeapon);
        npc.equipStack(EquipmentSlot.MAINHAND, food);
        foodSlot = bestSlot;
        npc.speech().notice(CompanionSpeech.Event.EAT);
        eatingTicks = 24; // Finish before vanilla's item use duration can consume it separately.
        npc.setEatingVisual(true);
        npc.getNavigation().stop();
        npc.setCurrentHand(Hand.MAIN_HAND);
    }

    private void finishEating() {
        ItemStack stack = npc.getMainHandStack();
        boolean bowl = stack.isOf(Items.MUSHROOM_STEW) || stack.isOf(Items.RABBIT_STEW)
                || stack.isOf(Items.BEETROOT_SOUP) || stack.isOf(Items.SUSPICIOUS_STEW);
        if (edible(stack)) {
            FoodComponent food = stack.getItem().getFoodComponent();
            stack.decrement(1);
            if (food != null) npc.heal(Math.max(2.0f, food.getHunger() * 0.8f));
        }
        restoreHand(stack);
        if (bowl) {
            ItemStack left=npc.backpack.addStack(new ItemStack(Items.BOWL));
            if(!left.isEmpty())npc.dropStack(left);
        }
    }

    public void cancelEating() {
        if (isEating()) restoreHand(npc.getMainHandStack());
    }

    private void restoreHand(ItemStack remainingFood) {
        if (foodSlot < 0) return;
        npc.clearActiveItem();
        ItemStack weapon = npc.backpack.removeStack(foodSlot);
        npc.equipStack(EquipmentSlot.MAINHAND, weapon);
        npc.backpack.setStack(foodSlot, remainingFood);
        npc.backpack.markDirty();
        foodSlot = -1;
        nextMealAt=npc.age+20;
        eatingTicks = 0;
        npc.setEatingVisual(false);
    }

    /** Same contact + sight predicate used by navigation and pickup to avoid waiting behind a wall. */
    public boolean canCollect(ItemEntity drop) {
        if(drop==null || !drop.isAlive() || drop.cannotPickup() || npc.menuOpen()
                || npc.squaredDistanceTo(drop)>2.7
                || !npc.getBoundingBox().expand(1.2).intersects(drop.getBoundingBox()))return false;
        var hit=npc.getWorld().raycast(new net.minecraft.world.RaycastContext(npc.getEyePos(),drop.getPos().add(0,0.1,0),
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS;
    }
    /** Pick up only physically nearby drops. The ItemEntity retains any remainder. */
    private void collectNearby() {
        for (ItemEntity drop : npc.getWorld().getEntitiesByClass(ItemEntity.class,
                npc.getBoundingBox().expand(1.2), e -> e.isAlive() && !e.cannotPickup() && !e.getStack().isEmpty())) {
            if(!canCollect(drop))continue;
            ItemStack picked=drop.getStack().copy();
            ItemStack incoming=drop.getStack().copy();
            ItemStack remainder = npc.backpack.addStack(incoming);
            int accepted=drop.getStack().getCount()-remainder.getCount();
            if(accepted>0){picked.setCount(accepted);if(!drop.getCommandTags().contains("aiplayer_recovered_support"))npc.gathering().onCollected(picked);}
            if (remainder.isEmpty()) drop.discard();
            else drop.setStack(remainder);
        }
    }
}
