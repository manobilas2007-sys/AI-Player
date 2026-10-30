package dev.aiplayer.test;

import dev.aiplayer.AIPlayer;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.ai.tasks.ProjectMaterials;
import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.entity.CompanionEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.UUID;

/** No resource coordinates are given to the worker. It must extend its own
 * physical survey beyond the original 32-block observation neighbourhood. */
public final class LongRangeResourceTests implements FabricGameTest {
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="long_range_material",tickLimit=10000)
    public void emptyInventoryDiscoversAndCollectsTimberBeyondOneHundredBlocks(TestContext c){
        // A separate island cannot alter arenas belonging to another test.
        BlockPos spawn=new BlockPos(8195,2,4100);
        BlockPos origin=c.getAbsolutePos(spawn);
        // Broad safe ground gives the expanding survey room to turn. Every
        // source is absent until the single tree more than 100 blocks away.
        for(int x=-90;x<=135;x++)for(int z=-90;z<=95;z++){
            BlockPos floor=origin.add(x,-1,z);c.getWorld().setBlockState(floor,Blocks.DIRT.getDefaultState());
            for(int y=0;y<4;y++)c.getWorld().setBlockState(origin.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        BlockPos tree=origin.add(105,0,-72);
        c.getWorld().setBlockState(tree,Blocks.OAK_LOG.getDefaultState());
        c.getWorld().setBlockState(tree.up(),Blocks.OAK_LOG.getDefaultState());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            c.getWorld().setBlockState(tree.add(x,2,z),Blocks.OAK_LEAVES.getDefaultState());
        CompanionEntity npc=c.spawnEntity(AIPlayer.COMPANION,spawn);
        NbtCompound data=new NbtCompound();npc.writeCustomDataToNbt(data);data.putUuid("Owner",UUID.randomUUID());npc.readCustomDataFromNbt(data);
        npc.autoCraft=true;npc.autoEquip=true;npc.autoLoot=true;npc.autoSurvive=false;npc.setMode(Mode.WAIT);
        npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,origin,null);
        ProjectMaterials materials=new ProjectMaterials(npc,new ProjectWork(npc));
        boolean[] drop={false};double[] furthest={0};
        c.runAtEveryTick(()->{
            materials.ensure(Items.OAK_LOG,1);
            furthest[0]=Math.max(furthest[0],npc.getBlockPos().getSquaredDistance(origin));
            if(!c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(tree).expand(3),
                e->e.isAlive() && e.getStack().isOf(Items.OAK_LOG)
                    && e.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).isEmpty())drop[0]=true;
            if(materials.count(Items.OAK_LOG)>=1){
                c.assertTrue(furthest[0]>100*100 && drop[0] && c.getWorld().getBlockState(tree).isAir(),
                    "Long search did not physically find, mine and pick up its distant natural tree");
                npc.discard();c.complete();
            }
        });
        c.runAtTick(9900,()->{
            c.assertTrue(materials.count(Items.OAK_LOG)>=1,"Survey stayed near the origin or never collected its real tree: "
                +npc.getPos()+" "+materials.diagnostics());npc.discard();c.complete();
        });
    }
}
