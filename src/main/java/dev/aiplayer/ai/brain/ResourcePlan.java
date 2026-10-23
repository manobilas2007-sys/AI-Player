package dev.aiplayer.ai.brain;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;

/** Recomputed from real inventory: no cached "craft success" or imaginary resources. */
public record ResourcePlan(int missingLogs,int missingFood,boolean toolsNeeded,int reservedLogs,boolean full) {
    public static boolean stoneCapacity(CompanionEntity npc){return npc.backpack.canInsert(new ItemStack(Items.COBBLESTONE));}
    public static int stoneNeeded(CompanionEntity npc){
        int need=(upgraded(npc,PickaxeItem.class)?0:3)+(upgraded(npc,AxeItem.class)?0:3)+(upgraded(npc,SwordItem.class)?0:2);
        return Math.max(0,need-npc.backpack.count(Items.COBBLESTONE));
    }
    private static boolean upgraded(CompanionEntity npc,Class<? extends Item> type){
        if(upgraded(npc.getMainHandStack(),type))return true;
        for(int i=0;i<npc.backpack.size();i++)if(upgraded(npc.backpack.getStack(i),type))return true;
        return false;
    }
    private static boolean upgraded(ItemStack stack,Class<? extends Item> type){
        return type.isInstance(stack.getItem()) && !worn(stack) && stack.getItem() instanceof ToolItem tool
            && tool.getMaterial().getMiningLevel()>=ToolMaterials.STONE.getMiningLevel();
    }
    public static boolean worn(ItemStack s) {
        return s.isDamageable() && s.getMaxDamage()-s.getDamage()<=Math.max(8,s.getMaxDamage()/20);
    }
    public static boolean food(ItemStack s) {
        return !s.isEmpty() && s.isFood() && !s.isOf(Items.ROTTEN_FLESH) && !s.isOf(Items.POISONOUS_POTATO)
            && !s.isOf(Items.SPIDER_EYE) && !s.isOf(Items.PUFFERFISH) && !s.isOf(Items.CHICKEN);
    }
    public static boolean usable(CompanionEntity npc,Class<? extends Item> type) {
        if(type.isInstance(npc.getMainHandStack().getItem()) && !worn(npc.getMainHandStack()))return true;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(type.isInstance(s.getItem()) && !worn(s))return true;
        }
        return false;
    }
    public static boolean foodCapacity(CompanionEntity npc){
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.isEmpty() || (food(stack) && stack.getCount()<stack.getMaxCount()))return true;
        }
        return false;
    }
    public static int buildingSlot(CompanionEntity npc) {
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE))return i;
        }
        int logs=0,slot=-1;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(s.isIn(ItemTags.LOGS) && s.getItem() instanceof BlockItem){logs+=s.getCount();slot=i;}
        }
        // Keep raw wood for rebuilding a bench/tool if everything else is lost.
        return logs>(npc.crafting().needsBasicTools()?2:1)?slot:-1;
    }
    public static ResourcePlan inspect(CompanionEntity npc) {
        int logs=0,planks=0,sticks=0,food=0,empty=0;
        boolean logSpace=false;
        boolean bench=npc.crafting().hasBench();
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(s.isEmpty())empty++;
            if(s.isIn(ItemTags.LOGS)){logs+=s.getCount();if(s.getCount()<s.getMaxCount())logSpace=true;}
            if(s.isIn(ItemTags.PLANKS))planks+=s.getCount();
            if(s.isOf(Items.STICK))sticks+=s.getCount();
            if(s.isOf(Items.CRAFTING_TABLE))bench=true;
            if(food(s))food+=s.getCount();
        }
        int materials=0,stickNeed=0;
        if(!usable(npc,AxeItem.class)){materials+=3;stickNeed+=2;}
        if(!usable(npc,PickaxeItem.class)){materials+=3;stickNeed+=2;}
        if(!usable(npc,SwordItem.class)){materials+=2;stickNeed++;}
        boolean tools=materials>0;
        int plankNeed=materials+(bench?0:4)+2*((Math.max(0,stickNeed-sticks)+3)/4);
        // Four reusable logs are reserved for a small pillar; never convert the entire reserve to sticks.
        int reserve=4;
        int missing=Math.max(0,(Math.max(0,plankNeed-planks)+3)/4+reserve-logs);
        boolean full=empty==0 && !logSpace;
        return new ResourcePlan(missing,Math.max(0,3-food),tools,reserve,full);
    }
}
