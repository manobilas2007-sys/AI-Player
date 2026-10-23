package dev.aiplayer.screen;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;

/** Live adapter, never a second copy of equipped items. */
final class EquipmentInventory implements Inventory {
    private final CompanionEntity npc;
    EquipmentInventory(CompanionEntity npc) {this.npc=npc;}
    public int size(){return 6;}
    public boolean isEmpty(){for(int i=0;i<6;i++)if(!getStack(i).isEmpty())return false;return true;}
    public ItemStack getStack(int slot){return npc.getEquippedStack(CompanionEntity.GEAR[slot]);}
    public ItemStack removeStack(int slot,int amount){ItemStack result=getStack(slot).split(amount);markDirty();return result;}
    public ItemStack removeStack(int slot){ItemStack result=getStack(slot);setStack(slot,ItemStack.EMPTY);return result;}
    public void setStack(int slot,ItemStack stack){npc.equipStack(CompanionEntity.GEAR[slot],stack);}
    public void markDirty(){}
    public boolean canPlayerUse(PlayerEntity p){return npc.isAlive() && npc.isOwner(p) && p.squaredDistanceTo(npc)<64;}
    public void clear(){for(int i=0;i<6;i++)setStack(i,ItemStack.EMPTY);}
}
