package dev.aiplayer.ai;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.enchantment.*;
import net.minecraft.item.*;

public final class EquipmentPolicy {
    private EquipmentPolicy() {}
    public static boolean accepts(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return false;
        if (slot.getType() == EquipmentSlot.Type.ARMOR)
            return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == slot;
        if (slot == EquipmentSlot.OFFHAND) return true;
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
    public static double score(ItemStack stack, EquipmentSlot slot, EntityGroup enemy) {
        if (!accepts(stack,slot)) return -1;
        double durability = stack.isDamageable() ? (double)(stack.getMaxDamage()-stack.getDamage())/stack.getMaxDamage() : 1;
        if (stack.getItem() instanceof ArmorItem armor)
            return armor.getProtection()*10 + armor.getToughness() + EnchantmentHelper.getLevel(Enchantments.PROTECTION,stack)*2 + durability*0.1;
        double damage = 1;
        for (var modifier : stack.getAttributeModifiers(slot).get(EntityAttributes.GENERIC_ATTACK_DAMAGE))
            damage += modifier.getValue();
        return (dev.aiplayer.ai.brain.ResourcePlan.worn(stack)?-2000:0) + (stack.getItem() instanceof SwordItem ? 1000 : 0)
            + damage + EnchantmentHelper.getAttackDamage(stack,enemy) + durability*0.01;
    }
}
