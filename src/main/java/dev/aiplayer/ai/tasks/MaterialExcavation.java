package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.*;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.function.Predicate;

/** Shared access to underground ingredients. Observe a cave before opening a
 * supported stair; inspect the rock exposed by real excavation, using indexed loaded chunks. */
public final class MaterialExcavation {
    private final CompanionEntity npc;
    private final ProjectWork work;
    private final SafeExcavation digging;
    private final ManualResourceSurvey survey;
    private final Predicate<BlockPos> permitted;
    private Item item;
    private int amount,searchTicks,routeStarted;
    private BlockPos entrance,lastAttempt,resourceGoal;
    private boolean active;
    private String status="Procurar acesso ao subsolo";
    public MaterialExcavation(CompanionEntity npc,ProjectWork work,PhysicalMining mining,Predicate<BlockPos> permitted){
        this.npc=npc;this.work=work;this.permitted=permitted;digging=new SafeExcavation(npc,mining);survey=new ManualResourceSurvey(npc);
        digging.configure(permitted,this::matches,p->npc.intelligence().restoration().removed(p));
    }
    public static boolean underground(Item item){return item==Items.COBBLESTONE || item==Items.COAL || item==Items.RAW_IRON;}
    private boolean matches(BlockState s){return item==Items.COBBLESTONE?s.isOf(Blocks.STONE)
        :item==Items.COAL?(s.isOf(Blocks.COAL_ORE)||s.isOf(Blocks.DEEPSLATE_COAL_ORE))
        :item==Items.RAW_IRON && (s.isOf(Blocks.IRON_ORE)||s.isOf(Blocks.DEEPSLATE_IRON_ORE));}
    public boolean active(){return active;}
    public boolean digging(){return digging.active();}
    public boolean pickupReady(){return !digging.active() || digging.pickupReady();}
    public boolean pickupPosition(BlockPos p){return !digging.active() || (permitted.test(p) && digging.pickupPosition(p));}
    public void pauseForPickup(){digging.pause();}
    public String status(){return status;}
    public String diagnostics(){return "active="+active+" item="+item+" amount="+amount+" entrance="+entrance+" lastAttempt="+lastAttempt+" search="+searchTicks+" corridor={"+digging.diagnostics()+"}";}
    public Item item(){return item;}
    public boolean travellingToCave(){return entrance!=null;}
    public void observe(Item requested,int quantity){
        if(active || !(npc.getWorld() instanceof ServerWorld world) || SurfaceStoneGathering.caveAccess(npc,npc.getBlockPos()))return;
        BlockPos cave=survey.observedCave(world);
        if(cave!=null && permitted.test(cave)){begin(requested,quantity);entrance=cave;routeStarted=npc.age;}
    }
    public void pause(){digging.pause();survey.pause();}
    public void cancel(){
        if(active)npc.intelligence().restoration().requestReturn();
        active=false;digging.stop();survey.reset();entrance=null;resourceGoal=null;item=null;searchTicks=0;
    }
    public void begin(Item requested,int quantity){
        if(active)return;item=requested;amount=quantity;active=true;searchTicks=0;entrance=null;survey.stop();
    }
    public void focus(Item requested,int quantity,BlockPos source){
        if(!active)begin(requested,quantity);
        if(item==requested)resourceGoal=source.toImmutable();
    }
    public boolean fulfilled(){return active && npc.backpack.count(item)+(npc.getMainHandStack().isOf(item)?npc.getMainHandStack().getCount():0)>=amount;}
    /** Called before parents consume a just-collected ingredient. */
    public boolean finishIfReady(){
        if(!fulfilled())return false;
        cancel();return npc.intelligence().restoration().pending();
    }
    public void tick(){
        if(!active || !(npc.getWorld() instanceof ServerWorld world))return;
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return;}
        if(npc.isTouchingWater()){digging.pause();npc.intelligence().recoverForWork();status="Sair da água antes de minerar";return;}
        if(fulfilled()){finishIfReady();return;}
        if(digging.active()){
            status="Escavar acesso e galeria com piso e caminho de volta";
            SafeExcavation.Result result=digging.tick();
            if(result==SafeExcavation.Result.BLOCKED || result==SafeExcavation.Result.FINISHED){
                npc.intelligence().restoration().requestReturn();active=false;searchTicks=0;
            }
            return;
        }
        if(entrance!=null){
            status="Entrar na caverna pelo caminho preparado";
            if(!work.moveTo(entrance)){
                if(npc.age-routeStarted>240){npc.intelligence().memory().failed(entrance,world.getTime());entrance=null;}
                return;
            }
            entrance=null;searchTicks=150;
        }
        if(searchTicks++<150){
            status="Identificar uma entrada de caverna segura";
            BlockPos observed=survey.observedCave(world);
            if(observed!=null && permitted.test(observed)){entrance=observed;routeStarted=npc.age;}
            return;
        }
        // Outside a worksite, the same stair can reach hidden stone or expose
        // a new ore-bearing face. Natural floors and liquid pockets are checked
        // at every step by the physical executor.
        if(lastAttempt!=null && npc.getBlockPos().getSquaredDistance(lastAttempt)<12*12){
            status="Procurar outro trecho de rocha, preservando a escavação anterior";survey.tick(true);return;
        }
        if(permitted.test(npc.getBlockPos()) && (resourceGoal==null?digging.beginStair():digging.beginStairToward(resourceGoal))){
            lastAttempt=npc.getBlockPos().toImmutable();
            npc.intelligence().restoration().begin(npc.getBlockPos());
            status="Abrir escada de acesso ao subsolo";return;
        }
        status="Procurar terreno seguro para acessar o subsolo";
        survey.tick(true);
    }
    public void writeNbt(NbtCompound root){
        NbtCompound n=new NbtCompound();if(resourceGoal!=null)n.putLong("ResourceGoal",resourceGoal.asLong());if(lastAttempt!=null)n.putLong("LastAttempt",lastAttempt.asLong());
        root.put("MaterialExcavation",n);if(!active)return;n.putString("Item",Registries.ITEM.getId(item).toString());n.putInt("Amount",amount);
        n.putInt("Search",searchTicks);if(entrance!=null)n.putLong("Entrance",entrance.asLong());
        NbtCompound corridor=new NbtCompound();digging.writeNbt(corridor);n.put("Corridor",corridor);root.put("MaterialExcavation",n);
    }
    public void readNbt(NbtCompound root){
        active=false;digging.stop();entrance=null;NbtCompound n=root.getCompound("MaterialExcavation");
        resourceGoal=n.contains("ResourceGoal")?BlockPos.fromLong(n.getLong("ResourceGoal")):null;
        lastAttempt=n.contains("LastAttempt")?BlockPos.fromLong(n.getLong("LastAttempt")):null;
        Identifier id=Identifier.tryParse(n.getString("Item"));if(id==null)return;
        Item restored=Registries.ITEM.get(id);if(!underground(restored) || n.getInt("Amount")<=0 || n.getInt("Amount")>2048)return;
        item=restored;amount=n.getInt("Amount");active=true;searchTicks=Math.min(150,Math.max(0,n.getInt("Search")));
        if(n.contains("Entrance"))entrance=BlockPos.fromLong(n.getLong("Entrance"));routeStarted=npc.age;
        digging.readNbt(n.getCompound("Corridor"));
    }
}
