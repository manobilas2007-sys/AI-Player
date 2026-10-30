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
public final class MaterialBatchRegressionTests implements FabricGameTest {
    private void demand(TestContext c,Item output,int quantity,Item raw,int expected){
        var npc=V070Fixtures.worker(c,new BlockPos(3,2,3));var materials=new ProjectMaterials(npc,new ProjectWork(npc));
        materials.demand(Map.of(output,quantity));var missing=materials.rawShortage();
        c.assertTrue(missing.getOrDefault(raw,0)==expected,"Wrong rounded dependency: "+missing);
        c.assertTrue(npc.backpack.isEmpty(),"Planning granted resources");c.complete();
    }
    private void stocks(TestContext c,int variant){
        var npc=V070Fixtures.worker(c,new BlockPos(3,2,3));var m=new ProjectMaterials(npc,new ProjectWork(npc));
        if(variant==0){npc.backpack.setStack(0,new ItemStack(Items.OAK_PLANKS,4));m.demand(Map.of(Items.OAK_STAIRS,4));c.assertTrue(m.rawShortage().get(Items.OAK_LOG)==1,"Existing planks not reserved");}
        if(variant==1){npc.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.OAK_LOG,2));m.demand(Map.of(Items.OAK_PLANKS,8));c.assertTrue(m.rawShortage().isEmpty(),"Held logs ignored");}
        if(variant==2){npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG,2));m.demand(Map.of(Items.OAK_LOG,3,Items.OAK_PLANKS,8));c.assertTrue(m.rawShortage().get(Items.OAK_LOG)==3,"Direct log and plank reservations overlapped");}
        if(variant==3){npc.backpack.setStack(0,new ItemStack(Items.COAL));m.demand(Map.of(Items.GLASS,10));c.assertTrue(m.rawShortage().get(Items.SAND)==10 && m.rawShortage().get(Items.OAK_LOG)==1,"Partially available fuel ignored: "+m.rawShortage());}
        if(variant==4){npc.backpack.setStack(0,new ItemStack(Items.COAL));m.demand(Map.of(Items.GLASS,8,Items.IRON_INGOT,8));c.assertTrue(m.rawShortage().getOrDefault(Items.OAK_LOG,0)>0,"One coal reserved twice");}
        if(variant==5){m.selectWood(Items.SPRUCE_LOG,Items.SPRUCE_PLANKS);npc.backpack.setStack(0,new ItemStack(Items.OAK_LOG,64));m.demand(Map.of(Items.SPRUCE_STAIRS,8));c.assertTrue(m.rawShortage().get(Items.SPRUCE_LOG)==3 && !m.rawShortage().containsKey(Items.OAK_LOG),"Mixed project species");}
        if(variant==6){m.demand(Map.of(Items.OAK_DOOR,1));NbtCompound n=new NbtCompound();m.writeNbt(n);var restored=new ProjectMaterials(npc,new ProjectWork(npc));restored.readNbt(n);c.assertTrue(restored.rawShortage().equals(m.rawShortage()),"Reservation lost on reload");}
        if(variant==7){m.demand(Map.of(Items.OAK_LOG,30));m.cancel();c.assertTrue(m.rawShortage().isEmpty(),"Cancelled project retained reservation");}
        if(variant==8){m.demand(Map.of(Items.OAK_LOG,30));m.demand(Map.of(Items.OAK_LOG,7));c.assertTrue(m.rawShortage().get(Items.OAK_LOG)==7,"Remaining bill accumulated completed work");}
        c.complete();
    }
    private void craft(TestContext c,Item output,int quantity,Item raw,int supplied,int expectedConsumed){
        V070Fixtures.floor(c,16,1,Blocks.DIRT);var npc=V070Fixtures.worker(c,new BlockPos(3,2,3));npc.autoCraft=true;npc.autoEquip=false;
        npc.backpack.setStack(0,new ItemStack(raw,supplied));BlockPos bench=c.getAbsolutePos(new BlockPos(4,2,3));
        c.getWorld().setBlockState(bench,Blocks.CRAFTING_TABLE.getDefaultState());OwnedBlocks.get(c.getWorld()).claim(bench,npc.getUuid());
        var m=new ProjectMaterials(npc,new ProjectWork(npc));m.preferWorkbench(bench);m.demand(Map.of(output,quantity));boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=m.ensureForProject(output,1);});
        c.runAtTick(400,()->{c.assertTrue(done[0] && m.count(output)>=quantity,"Batch never completed: "+m.status());c.assertTrue(m.count(raw)==supplied-expectedConsumed,"Wrong actual recipe consumption");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void plankBatch(TestContext c){demand(c,Items.OAK_PLANKS,8,Items.OAK_LOG,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void stairsRoundedYield(TestContext c){demand(c,Items.OAK_STAIRS,8,Items.OAK_LOG,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void slabsRoundedYield(TestContext c){demand(c,Items.OAK_SLAB,7,Items.OAK_LOG,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void doorsRoundedYield(TestContext c){demand(c,Items.OAK_DOOR,4,Items.OAK_LOG,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void trapdoorsRoundedYield(TestContext c){demand(c,Items.OAK_TRAPDOOR,3,Items.OAK_LOG,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void fencesShareStickSupply(TestContext c){demand(c,Items.OAK_FENCE,4,Items.OAK_LOG,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void gateSharesPlankSupply(TestContext c){demand(c,Items.OAK_FENCE_GATE,1,Items.OAK_LOG,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void laddersRoundStickBatches(TestContext c){demand(c,Items.LADDER,4,Items.OAK_LOG,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void chestBatch(TestContext c){demand(c,Items.CHEST,2,Items.OAK_LOG,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void furnaceBatch(TestContext c){demand(c,Items.FURNACE,2,Items.COBBLESTONE,16);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void bucketSmeltingInput(TestContext c){demand(c,Items.BUCKET,2,Items.RAW_IRON,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void reserveExistingPlanks(TestContext c){stocks(c,0);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void reserveHeldLogs(TestContext c){stocks(c,1);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void avoidDoubleCountingDirectLogs(TestContext c){stocks(c,2);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void partialCoalFuel(TestContext c){stocks(c,3);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void sharedCoalUsedOnce(TestContext c){stocks(c,4);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void paletteIsStable(TestContext c){stocks(c,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void billSurvivesSave(TestContext c){stocks(c,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void cancelClearsBill(TestContext c){stocks(c,7);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=100)
    public void completedPartsReduceBill(TestContext c){stocks(c,8);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=450)
    public void craftWholePlankBatch(TestContext c){craft(c,Items.OAK_PLANKS,20,Items.OAK_LOG,8,5);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=450)
    public void craftWholeStairBatch(TestContext c){craft(c,Items.OAK_STAIRS,8,Items.OAK_PLANKS,20,12);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=450)
    public void craftRoundedDoorBatch(TestContext c){craft(c,Items.OAK_DOOR,4,Items.OAK_PLANKS,20,12);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=450)
    public void craftRoundedSlabBatch(TestContext c){craft(c,Items.OAK_SLAB,7,Items.OAK_PLANKS,12,6);}
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=450)
    public void craftTwoFurnaces(TestContext c){craft(c,Items.FURNACE,2,Items.COBBLESTONE,20,16);}

    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=600)
    public void partialLotRemainsUsableUntilConsumed(TestContext c){
        V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.autoCraft=true;n.autoEquip=false;
        n.backpack.setStack(0,new ItemStack(Items.OAK_LOG,3));var m=new ProjectMaterials(n,new ProjectWork(n));m.demand(Map.of(Items.OAK_PLANKS,80));boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=m.ensureForProject(Items.OAK_PLANKS,1);});
        c.runAtTick(500,()->{
            c.assertTrue(done[0] && m.count(Items.OAK_PLANKS)==12,"Available partial lot was starved by full project bill: "+m.diagnostics());
            for(int slot=0;slot<n.backpack.size();slot++)if(n.backpack.getStack(slot).isOf(Items.OAK_PLANKS)){n.backpack.removeStack(slot,6);break;}
            c.assertTrue(m.ensureForProject(Items.OAK_PLANKS,1) && !m.hasPending(),"Delivered lot restarted gathering before consumption");c.complete();
        });
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=200)
    public void legacyPendingAmountSurvivesLargerProjectBill(TestContext c){
        V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.autoCraft=true;n.autoEquip=false;n.backpack.setStack(0,new ItemStack(Items.OAK_LOG));
        NbtCompound root=new NbtCompound(),saved=new NbtCompound();saved.putInt("Version",1);saved.putString("Dimension",c.getWorld().getRegistryKey().getValue().toString());saved.putString("PendingItem","minecraft:oak_planks");saved.putInt("PendingAmount",1);root.put("ProjectMaterials",saved);
        var m=new ProjectMaterials(n,new ProjectWork(n));m.readNbt(root);m.demand(Map.of(Items.OAK_PLANKS,100));boolean[] done={false};
        c.runAtEveryTick(()->{if(!done[0])done[0]=m.resumePending();});
        c.runAtTick(120,()->{c.assertTrue(done[0] && m.count(Items.OAK_PLANKS)==4 && m.count(Items.OAK_LOG)==0 && !m.hasPending(),"Legacy trip silently expanded to full house demand");c.complete();});
    }
    @GameTest(templateName="aiplayer_test:surface_arena",batchId="v070_batches",tickLimit=650)
    public void partialLotDeliverySurvivesMidRecipeReload(TestContext c){
        V070Fixtures.floor(c,16,1,Blocks.DIRT);var n=V070Fixtures.worker(c,new BlockPos(4,2,4));n.autoCraft=true;n.autoEquip=false;n.backpack.setStack(0,new ItemStack(Items.OAK_LOG,3));
        ProjectMaterials[] m={new ProjectMaterials(n,new ProjectWork(n))};m[0].demand(Map.of(Items.OAK_PLANKS,80));boolean[] reloaded={false},done={false};
        c.runAtEveryTick(()->{
            if(!done[0])done[0]=m[0].ensureForProject(Items.OAK_PLANKS,1);
            if(!reloaded[0] && m[0].count(Items.OAK_PLANKS)>=4){NbtCompound data=new NbtCompound();m[0].writeNbt(data);m[0].pause();m[0]=new ProjectMaterials(n,new ProjectWork(n));m[0].readNbt(data);reloaded[0]=true;}
        });
        c.runAtTick(550,()->{c.assertTrue(reloaded[0] && done[0] && m[0].count(Items.OAK_PLANKS)==12,"Reload lost partial lot delivery: "+m[0].diagnostics());c.complete();});
    }
}
