package dev.aiplayer.screen;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.player.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public class CompanionMenu extends ScreenHandler {
    public final CompanionEntity npc;
    private final PropertyDelegate state;
    public CompanionMenu(int id,PlayerInventory player,PacketByteBuf buf) {
        this(id,player,(CompanionEntity)player.player.getWorld().getEntityById(buf.readInt()),true);
    }
    public CompanionMenu(int id,PlayerInventory player,CompanionEntity npc) {this(id,player,npc,false);}
    private CompanionMenu(int id,PlayerInventory player,CompanionEntity npc,boolean client) {
        super(AIPlayer.MENU,id);this.npc=npc;
        Inventory bag=client?new SimpleInventory(27):npc.backpack;
        Inventory gear=client?new SimpleInventory(6):new EquipmentInventory(npc);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(bag,col+row*9,8+col*18,62+row*18));
        for(int i=0;i<6;i++) {
            final EquipmentSlot type=CompanionEntity.GEAR[i];
            addSlot(new Slot(gear,i,8+i*27,128) {
                @Override public boolean canInsert(ItemStack stack) {
                    return type==EquipmentSlot.MAINHAND || type==EquipmentSlot.OFFHAND || EquipmentPolicy.accepts(stack,type);
                }
                @Override public int getMaxItemCount(){return 1;}
                @Override public boolean canTakeItems(PlayerEntity p){return p.isCreative() || !EnchantmentHelper.hasBindingCurse(getStack());}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,156+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,214));
        state=client?new ArrayPropertyDelegate(3):new PropertyDelegate(){
            public int size(){return 3;}
            public int get(int i){return switch(i){case 0->npc.mode().ordinal();case 1->(int)Math.ceil(npc.getHealth());default->npc.autoEquip?1:0;};}
            public void set(int i,int v){}
        };
        addProperties(state); if(!client)npc.opened();
    }
    public int value(int i){return state.get(i);}
    @Override public boolean canUse(PlayerEntity player){return npc!=null && npc.isAlive() && npc.isOwner(player) && player.squaredDistanceTo(npc)<64;}
    @Override public void onClosed(PlayerEntity p){super.onClosed(p);if(!p.getWorld().isClient && npc!=null)npc.closed();}
    @Override public boolean onButtonClick(PlayerEntity p,int id) {
        if(p.getWorld().isClient || !canUse(p))return false;
        if(id>=0 && id<=2)npc.setMode(Mode.fromId(id));
        else if(id==3) {
            if(!CompanionGoal.recall(npc,p))p.sendMessage(Text.literal("Não há espaço seguro perto de você."),true);
        } else if(id==4) {npc.autoEquip=!npc.autoEquip;if(npc.autoEquip)npc.optimizeEquipment();}
        else return false;
        sendContentUpdates();return true;
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index) {
        if(!canUse(player) || index<0 || index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasStack() || !slot.canTakeItems(player))return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<33) {if(!insertItem(stack,33,69,true))return ItemStack.EMPTY;}
        else {if(!insertItem(stack,0,27,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();
        slot.onTakeItem(player,stack);return original;
    }
}
