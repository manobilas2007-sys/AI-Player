package dev.aiplayer.test;
import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.*;
import dev.aiplayer.ai.tasks.*;
import dev.aiplayer.ai.navigation.*;
import dev.aiplayer.compat.FallingTreeBridge;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import java.util.*;
final class V070Fixtures {
    static void floor(TestContext c,int size,int y,Block block){for(int x=0;x<size;x++)for(int z=0;z<size;z++)c.setBlockState(new BlockPos(x,y,z),block);}
    static CompanionEntity worker(TestContext c,BlockPos at){
        long seed=Long.getLong("aiplayer.gametest.seed",0L);
        c.assertTrue(c.getWorld().getSeed()==seed,"GameTest world did not apply requested seed "+seed);
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,at);npc.getRandom().setSeed(seed ^ at.asLong());
        NbtCompound data=new NbtCompound();npc.writeCustomDataToNbt(data);data.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(data);
        npc.setMode(Mode.WAIT);npc.autoCraft=false;npc.autoSurvive=false;npc.autoEquip=true;npc.autoLoot=true;return npc;
    }
    static Block block(String id){return Registries.BLOCK.get(new Identifier(id));}
    static Item item(String id){return Registries.ITEM.get(new Identifier(id));}
    static int drops(TestContext c,BlockPos at,Item item){return c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(at).expand(16),e->e.isAlive() && e.getStack().isOf(item)).stream().mapToInt(e->e.getStack().getCount()).sum();}
    static void tree(TestContext c,BlockPos base,String species,int height,int width){
        for(int x=0;x<width;x++)for(int z=0;z<width;z++){
            c.getWorld().setBlockState(base.add(x,-1,z),Blocks.DIRT.getDefaultState());
            for(int y=0;y<height;y++)c.getWorld().setBlockState(base.add(x,y,z),block(species+"_log").getDefaultState());
        }
        for(int x=-1;x<=width;x++)for(int z=-1;z<=width;z++)c.getWorld().setBlockState(base.add(x,height,z),block(species+"_leaves").getDefaultState());
    }
}
