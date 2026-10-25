package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.PhysicalMining;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;

/** A resumable dependency executor for project materials. Recipes consume real
 * stacks; smelting is performed by a placed vanilla furnace. Mining creates
 * normal world drops, and the existing physical pickup system collects them. */
public final class ProjectMaterials {
    private record Recipe(Item output,int amount,Map<Item,Integer> ingredients,boolean bench) {}
    private static final int CRAFT_TICKS=12,SCAN_PER_TICK=384,SEARCH_RADIUS=32;
    private final CompanionEntity npc;
    private final ProjectWork work;
    private final PhysicalMining mining;
    private Item log=Items.OAK_LOG,planks=Items.OAK_PLANKS;
    private Item miningFor,scanFor,crafting;
    private BlockPos target,scanOrigin,furnace,bench,preferredFurnace,preferredBench;
    private Box worksite;
    private int scanRadius,scanY,scanEdge,craftTicks,scans,searchAt,targetSince,nextRoute;
    private int lastEnsure=-1;
    private int woodHarvests;
    private boolean preparingAxe,axePending,woodReceived;
    private double bestTargetDistance=Double.MAX_VALUE;
    private final Map<UUID,Integer> avoidedDrops=new HashMap<>();
    private UUID dropTarget;
    private int dropProgressAt;
    private double bestDropDistance=Double.MAX_VALUE;
    private String dimension="",status="Conferir materiais do projeto";

    public ProjectMaterials(CompanionEntity npc,ProjectWork work){
        this.npc=npc;this.work=work;this.mining=new PhysicalMining(npc,this::allowedTool);
    }
    public void selectWood(Item selectedLog,Item selectedPlanks){
        if(selectedLog==null || selectedPlanks==null || !(selectedLog instanceof BlockItem)
                || !(selectedPlanks instanceof BlockItem))return;
        String name=Registries.ITEM.getId(selectedLog).getPath();
        if(!name.endsWith("_log") || !Registries.ITEM.getId(selectedPlanks).getPath()
                .equals(name.substring(0,name.length()-4)+"_planks"))return;
        if(log!=selectedLog || planks!=selectedPlanks){target=null;scanFor=null;mining.reset();}
        log=selectedLog;planks=selectedPlanks;
    }
    public void preferFurnace(BlockPos pos){preferredFurnace=pos==null?null:pos.toImmutable();}
    public void preferWorkbench(BlockPos pos){preferredBench=pos==null?null:pos.toImmutable();}
    /** Material prospecting must not dismantle the terrain the active project
     * is preparing. Real drops from its explicit preparation remain usable. */
    public void keepOut(Box area){worksite=area;}
    public BlockPos furnacePos(){return furnace;}
    public String status(){return status;}
    public String diagnostics(){
        String targetState="none",drop="none";
        if(npc.getWorld() instanceof ServerWorld world){
            if(target!=null)targetState=world.isChunkLoaded(target)?world.getBlockState(target).toString():"unloaded";
            if(dropTarget!=null){
                var entity=world.getEntity(dropTarget);
                drop=entity instanceof ItemEntity item?item.getStack()+"@"+item.getPos():"missing:"+dropTarget;
            }
        }
        ItemStack held=npc.getMainHandStack();
        String allowedHand=target==null || npc.getWorld().isChunkLoaded(target)?Boolean.toString(allowedTool(held)):"unloaded";
        String picks="";
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.getItem() instanceof PickaxeItem)picks+=" "+i+":"+stack+"("+stack.getDamage()+"/"+stack.getMaxDamage()+")";
        }
        return "materialTarget="+target+" state="+targetState+" for="+itemId(miningFor)
            +" targetAge="+(npc.age-targetSince)+" best="+bestTargetDistance
            +" mining="+mining.target()+"/"+mining.hasProgress()+" hand="+held+"("+held.getDamage()+"/"+held.getMaxDamage()+")"
            +" allowedHand="+allowedHand+" picks="+picks
            +" scan="+itemId(scanFor)+"@"+scanOrigin+":"+scanRadius+","+scanY+","+scanEdge
            +" searchWait="+(searchAt-npc.age)+" drop="+drop+" dropAge="+(npc.age-dropProgressAt)
            +" recipe="+itemId(crafting)+":"+craftTicks+" axePending="+axePending+" woodReceived="+woodReceived
            +" bench="+bench+" preferredBench="+preferredBench+" furnace="+furnace+" preferredFurnace="+preferredFurnace;
    }
    private String itemId(Item item){return item==null?"none":Registries.ITEM.getId(item).toString();}
    public int count(Item item){
        int result=npc.backpack.count(item);
        if(npc.getMainHandStack().isOf(item))result+=npc.getMainHandStack().getCount();
        return result;
    }
    public void pause(){mining.reset();npc.clearWorkingVisual();}
    public void cancel(){
        pause();target=null;scanFor=null;miningFor=null;crafting=null;craftTicks=0;
        // Stations belong to the cancelled project, not to every future job.
        // Keeping an old preferred site can route the next build back to a
        // distant farm instead of creating its own nearby workbench/furnace.
        furnace=null;bench=null;preferredFurnace=null;preferredBench=null;
        worksite=null;dimension="";scanOrigin=null;
        scanRadius=0;scanY=0;scanEdge=0;scans=0;searchAt=0;targetSince=0;nextRoute=0;
        bestTargetDistance=Double.MAX_VALUE;dropProgressAt=0;
        dropTarget=null;bestDropDistance=Double.MAX_VALUE;avoidedDrops.clear();lastEnsure=-1;
        woodHarvests=0;preparingAxe=false;axePending=false;woodReceived=false;
        status="Conferir materiais do projeto";
    }

    /** Advance at most one dependency leaf per call. True always means actual
     * inventory already contains the requested quantity, including the hand. */
    public boolean ensure(Item item,int amount){
        if(amount<=0)return true;
        if(item==null || item==Items.AIR || amount>2048){status="Material inválido para o projeto";return false;}
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread())return false;
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return false;}
        String current=world.getRegistryKey().getValue().toString();
        if(!dimension.isEmpty() && !dimension.equals(current)){cancel();furnace=null;bench=null;preferredFurnace=null;preferredBench=null;}
        dimension=current;
        // A broken block still has a pickup delay. Only actual inventory is
        // proof that the project's first wood has been physically delivered.
        if(count(log)>0 || count(planks)>0)woodReceived=true;
        if(npc.autoCraft && npc.autoEquip && !ResourcePlan.usable(npc,AxeItem.class) && wornAxe())axePending=true;
        if(axePending && ResourcePlan.usable(npc,AxeItem.class))axePending=false;
        // Reserve this subgoal's real logs/planks across ticks. Checking the
        // parent count first would hand each newly collected log straight to
        // the next floor/border block and starve the axe recipe forever.
        if(axePending && npc.autoCraft && npc.autoEquip){
            if(lastEnsure==npc.age)return false;
            lastEnsure=npc.age;
            prepareAxe(world);return false;
        }
        if(count(item)>=amount)return true;
        if(lastEnsure==npc.age)return false;
        lastEnsure=npc.age;
        if(!npc.autoCraft){status="Entregue "+label(item)+" ou ative fabricação automática";return false;}
        return ensure(world,item,amount,0);
    }
    private void prepareAxe(ServerWorld world){
        preparingAxe=true;
        try{ensure(world,Items.WOODEN_AXE,count(Items.WOODEN_AXE)+1,0);}
        finally{preparingAxe=false;}
        if(ResourcePlan.usable(npc,AxeItem.class))axePending=false;
    }
    private boolean ensure(ServerWorld world,Item item,int amount,int depth){
        if(count(item)>=amount)return true;
        if(depth>14){status="Não consegui resolver os materiais de "+label(item);return false;}
        if(item==Items.IRON_INGOT || item==Items.GLASS || item==Items.CHARCOAL)return smelt(world,item,depth+1);
        Recipe recipe=recipe(item);
        if(recipe!=null){
            for(var ingredient:recipe.ingredients().entrySet())
                if(!ensure(world,ingredient.getKey(),ingredient.getValue(),depth+1))return false;
            if(recipe.bench() && !workbench(world,depth+1))return false;
            status="Fabricar "+label(item)+" com ingredientes reais";
            npc.getNavigation().stop();npc.markWorking();npc.animateInteraction(CompanionEntity.CRAFT,3);
            if(crafting!=item){crafting=item;craftTicks=0;}
            if(++craftTicks<CRAFT_TICKS)return false;
            if(!craft(recipe)){status="Mochila sem espaço para "+label(item);craftTicks=0;return false;}
            crafting=null;craftTicks=0;return false;
        }
        if(item==log || item==Items.COBBLESTONE || item==Items.SAND || item==Items.COAL
                || item==Items.RAW_IRON || item==Items.DIRT)return gather(world,item,depth+1);
        status="Preciso de "+label(item)+" real para continuar";return false;
    }
    private String label(Item item){return item.getName().getString();}
    private Item registered(String path){return Registries.ITEM.get(new Identifier("minecraft",path));}
    private String species(){String path=Registries.ITEM.getId(planks).getPath();return path.substring(0,path.length()-7);}
    private Recipe recipe(Item item){
        if(item==planks){
            Item source=log;
            for(String candidate:new String[]{species()+"_log",species()+"_wood","stripped_"+species()+"_log","stripped_"+species()+"_wood"}){
                Item possible=registered(candidate);if(possible!=Items.AIR && count(possible)>0){source=possible;break;}
            }
            return recipe(item,4,false,source,1);
        }
        if(item==Items.STICK)return recipe(item,4,false,planks,2);
        if(item==Items.CRAFTING_TABLE)return recipe(item,1,false,planks,4);
        if(item==Items.FURNACE)return recipe(item,1,true,Items.COBBLESTONE,8);
        if(item==Items.BUCKET)return recipe(item,1,true,Items.IRON_INGOT,3);
        if(item==Items.GLASS_PANE)return recipe(item,16,true,Items.GLASS,6);
        if(item==Items.CHEST)return recipe(item,1,true,planks,8);
        if(item==Items.LADDER)return recipe(item,3,true,Items.STICK,7);
        if(item==Items.WOODEN_PICKAXE || item==Items.WOODEN_AXE)return recipe(item,1,true,planks,3,Items.STICK,2);
        if(item==Items.STONE_PICKAXE || item==Items.STONE_AXE)return recipe(item,1,true,Items.COBBLESTONE,3,Items.STICK,2);
        if(item==Items.WOODEN_HOE)return recipe(item,1,true,planks,2,Items.STICK,2);
        if(item==Items.STONE_HOE)return recipe(item,1,true,Items.COBBLESTONE,2,Items.STICK,2);
        if(item==Items.TORCH)return recipe(item,4,false,count(Items.COAL)>0?Items.COAL:Items.CHARCOAL,1,Items.STICK,1);
        String path=Registries.ITEM.getId(item).getPath();
        if(path.equals(species()+"_stairs"))return recipe(item,4,true,planks,6);
        if(path.equals(species()+"_slab"))return recipe(item,6,true,planks,3);
        if(path.equals(species()+"_door"))return recipe(item,3,true,planks,6);
        if(path.equals(species()+"_trapdoor"))return recipe(item,2,true,planks,6);
        if(path.equals(species()+"_fence"))return recipe(item,3,true,planks,4,Items.STICK,2);
        if(path.equals(species()+"_fence_gate"))return recipe(item,1,true,planks,2,Items.STICK,4);
        return null;
    }
    private Recipe recipe(Item output,int amount,boolean bench,Object... pairs){
        Map<Item,Integer> ingredients=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)ingredients.put((Item)pairs[i],(Integer)pairs[i+1]);
        return new Recipe(output,amount,ingredients,bench);
    }
    private boolean consume(Item item,int amount){
        if(count(item)<amount)return false;
        int remaining=amount;
        for(int i=0;i<npc.backpack.size() && remaining>0;i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.isOf(item)){int used=Math.min(remaining,stack.getCount());npc.backpack.removeStack(i,used);remaining-=used;}
        }
        if(remaining>0 && npc.getMainHandStack().isOf(item)){
            npc.getMainHandStack().decrement(remaining);
            if(npc.getMainHandStack().isEmpty())npc.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        npc.backpack.markDirty();return true;
    }
    private boolean craft(Recipe recipe){
        SimpleInventory capacity=new SimpleInventory(npc.backpack.size());
        for(int i=0;i<npc.backpack.size();i++)capacity.setStack(i,npc.backpack.getStack(i).copy());
        for(var ingredient:recipe.ingredients().entrySet()){
            if(count(ingredient.getKey())<ingredient.getValue())return false;
            int left=ingredient.getValue();
            for(int i=0;i<capacity.size() && left>0;i++)if(capacity.getStack(i).isOf(ingredient.getKey())){
                int n=Math.min(left,capacity.getStack(i).getCount());capacity.removeStack(i,n);left-=n;
            }
        }
        if(!capacity.addStack(new ItemStack(recipe.output(),recipe.amount())).isEmpty())return false;
        for(var ingredient:recipe.ingredients().entrySet())if(!consume(ingredient.getKey(),ingredient.getValue()))return false;
        ItemStack overflow=npc.backpack.addStack(new ItemStack(recipe.output(),recipe.amount()));
        if(!overflow.isEmpty())npc.dropStack(overflow);
        if(recipe.output() instanceof AxeItem){
            // The replacement must exist before retiring a worn tool.
            for(int i=0;i<npc.backpack.size();i++){
                ItemStack stack=npc.backpack.getStack(i);
                if(stack.getItem() instanceof AxeItem && ResourcePlan.worn(stack))npc.backpack.setStack(i,ItemStack.EMPTY);
            }
            ItemStack held=npc.getMainHandStack();
            if(held.getItem() instanceof AxeItem && ResourcePlan.worn(held))npc.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        npc.backpack.markDirty();return true;
    }
    private boolean accessible(ServerWorld world,BlockPos pos){
        return pos!=null && world.isChunkLoaded(pos) && mining.reachable(pos);
    }
    private boolean workbench(ServerWorld world,int depth){
        if(bench!=null && world.isChunkLoaded(bench) && world.getBlockState(bench).isOf(Blocks.CRAFTING_TABLE)){
            status="Usar bancada do projeto";return work.approach(bench);
        }
        for(BlockPos pos:BlockPos.iterate(npc.getBlockPos().add(-3,-1,-3),npc.getBlockPos().add(3,1,3)))
            if(world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE) && accessible(world,pos)){
                bench=pos.toImmutable();return true;
            }
        if(!ensure(world,Items.CRAFTING_TABLE,1,depth+1))return false;
        BlockPos spot=preferredBench!=null?preferredBench:stationSpot(world);
        if(spot==null){status="Preciso de espaço seguro para colocar a bancada";return false;}
        if(!clearStation(world,spot))return false;
        status="Colocar bancada real para fabricar";
        if(work.place(spot,Blocks.CRAFTING_TABLE.getDefaultState(),Items.CRAFTING_TABLE,1))bench=spot.toImmutable();
        return false;
    }
    private BlockPos stationSpot(ServerWorld world){
        BlockPos here=npc.getBlockPos();
        for(int radius=1;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos pos=here.add(dx,0,dz);
            if(world.isChunkLoaded(pos) && world.isChunkLoaded(pos.down()) && work.replaceable(pos)
                    && (worksite==null || !worksite.contains(Vec3d.ofCenter(pos)))
                    && world.getFluidState(pos).isEmpty() && world.getBlockState(pos.down()).isSolidBlock(world,pos.down())
                    && !OwnedBlocks.get(world).protectedBlock(pos) && !OwnedBlocks.get(world).claimed(pos)
                    && world.getOtherEntities(npc,new Box(pos)).isEmpty())return pos.toImmutable();
        }
        return null;
    }
    private boolean clearStation(ServerWorld world,BlockPos spot){
        if(!world.isChunkLoaded(spot) || work.protectedPosition(spot)){
            status="Preservar local protegido da estação de trabalho";return false;
        }
        if(world.getBlockState(spot).isAir())return true;
        if(!work.replaceable(spot)){
            status="Preciso de espaço livre sem desmontar construções para a estação";return false;
        }
        status="Retirar vegetação ou neve antes de colocar a estação de trabalho";
        if(!work.clearNatural(spot))status=work.status();
        return false;
    }
    private FurnaceBlockEntity furnace(ServerWorld world,int depth){
        if(preferredFurnace!=null && world.isChunkLoaded(preferredFurnace)
                && world.getBlockEntity(preferredFurnace) instanceof FurnaceBlockEntity
                && OwnedBlocks.get(world).owned(preferredFurnace,npc.getUuid()))furnace=preferredFurnace;
        if(furnace!=null && world.isChunkLoaded(furnace) && OwnedBlocks.get(world).owned(furnace,npc.getUuid())
                && world.getBlockEntity(furnace) instanceof FurnaceBlockEntity found)return found;
        furnace=null;
        if(!ensure(world,Items.FURNACE,1,depth+1))return null;
        BlockPos spot=preferredFurnace!=null?preferredFurnace:stationSpot(world);
        if(spot==null){status="Preciso de espaço seguro para a fornalha";return null;}
        if(!clearStation(world,spot))return null;
        status="Colocar fornalha real para fundir materiais";
        if(work.place(spot,Blocks.FURNACE.getDefaultState(),Items.FURNACE,1))furnace=spot.toImmutable();
        return null;
    }
    private boolean smelt(ServerWorld world,Item output,int depth){
        FurnaceBlockEntity oven=furnace(world,depth+1);
        if(oven==null)return false;
        status="Ir até a fornalha do projeto";
        if(!work.approach(furnace))return false;
        if(!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)){
            status="As regras do mundo impedem usar a fornalha do projeto";return false;
        }
        ItemStack finished=oven.getStack(2);
        if(!finished.isEmpty()){
            ItemStack remainder=npc.backpack.addStack(finished.copy());
            int accepted=finished.getCount()-remainder.getCount();
            if(accepted>0){oven.removeStack(2,accepted);oven.markDirty();npc.backpack.markDirty();}
            status=accepted>0?"Retirar produto real da fornalha":"Mochila sem espaço para o produto da fornalha";
            return false;
        }
        ItemStack input=oven.getStack(0),fuel=oven.getStack(1);
        int burning=oven.createNbt().getShort("BurnTime");
        if(!input.isEmpty()){
            if(fuel.isEmpty() && burning<=0){
                Item available=fuelAvailable(input.isOf(log));
                if(available==null){if(!ensure(world,planks,1,depth+1))return false;available=planks;}
                if(consume(available,1)){oven.setStack(1,new ItemStack(available));oven.markDirty();}
            }
            status="Aguardar fundição real: "+label(input.getItem());npc.markWorking();return false;
        }
        Item raw=output==Items.IRON_INGOT?Items.RAW_IRON:output==Items.GLASS
            ?(count(Items.RED_SAND)>0?Items.RED_SAND:Items.SAND):log;
        if(!ensure(world,raw,1,depth+1))return false;
        Item available=fuelAvailable(output==Items.CHARCOAL);
        if(fuel.isEmpty() && burning<201 && available==null){
            if(output==Items.CHARCOAL){if(!ensure(world,planks,1,depth+1))return false;available=planks;}
            else {if(!ensure(world,Items.CHARCOAL,1,depth+1))return false;available=Items.CHARCOAL;}
        }
        // Reserve fuel before adding input, so a charcoal prerequisite cannot
        // become blocked behind an unfuelled iron/sand stack in this furnace.
        if(fuel.isEmpty() && burning<201 && available!=null && consume(available,1))oven.setStack(1,new ItemStack(available));
        if(consume(raw,1)){oven.setStack(0,new ItemStack(raw));oven.markDirty();npc.animateInteraction(CompanionEntity.CRAFT,6);}
        status="Fundir "+label(raw)+" na fornalha";return false;
    }
    private Item fuelAvailable(boolean charcoal){
        if(count(Items.COAL)>0)return Items.COAL;
        if(!charcoal && count(Items.CHARCOAL)>0)return Items.CHARCOAL;
        return charcoal && count(planks)>0?planks:null;
    }
    private boolean allowedTool(ItemStack stack){
        if(!npc.autoEquip && stack!=npc.getMainHandStack())return false;
        if(target!=null && miningFor!=null && (miningFor==Items.COBBLESTONE || miningFor==Items.RAW_IRON || miningFor==Items.COAL)){
            BlockState state=npc.getWorld().getBlockState(target);
            return stack.getItem() instanceof PickaxeItem && !ResourcePlan.worn(stack) && stack.isSuitableFor(state);
        }
        return true;
    }
    private boolean suitablePick(BlockState state){
        if(npc.getMainHandStack().getItem() instanceof PickaxeItem && !ResourcePlan.worn(npc.getMainHandStack())
                && npc.getMainHandStack().isSuitableFor(state))return true;
        if(!npc.autoEquip)return false;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.getItem() instanceof PickaxeItem && !ResourcePlan.worn(stack) && stack.isSuitableFor(state))return true;
        }
        return false;
    }
    private boolean matches(BlockState state,Item item){
        return item==log?state.isOf(((BlockItem)log).getBlock())
            :item==Items.COBBLESTONE?state.isOf(Blocks.STONE)
            :item==Items.SAND?state.isOf(Blocks.SAND)
            :item==Items.COAL?(state.isOf(Blocks.COAL_ORE)||state.isOf(Blocks.DEEPSLATE_COAL_ORE))
            :item==Items.RAW_IRON?(state.isOf(Blocks.IRON_ORE)||state.isOf(Blocks.DEEPSLATE_IRON_ORE))
            :item==Items.DIRT?(state.isOf(Blocks.DIRT)||state.isOf(Blocks.GRASS_BLOCK)):false;
    }
    private boolean safeTarget(ServerWorld world,BlockPos pos,Item item){
        if(worksite!=null && worksite.contains(Vec3d.ofCenter(pos)))return false;
        if(!world.isChunkLoaded(pos) || !matches(world.getBlockState(pos),item) || world.getBlockEntity(pos)!=null
                || !world.getFluidState(pos).isEmpty() || OwnedBlocks.get(world).protectedBlock(pos)
                || OwnedBlocks.get(world).claimed(pos) || npc.intelligence().memory().blocked(pos,world.getTime()))return false;
        // Never mine a support underneath any actor, even if it is natural.
        if(pos.equals(npc.getBlockPos().down()) || !world.getEntitiesByClass(LivingEntity.class,
                new Box(pos.up()).expand(0.01),LivingEntity::isAlive).isEmpty())return false;
        for(Direction side:Direction.values()){
            BlockPos neighbor=pos.offset(side);
            if(!world.isChunkLoaded(neighbor))return false;
            if(!world.getFluidState(neighbor).isEmpty())return false;
        }
        // The actual ray below decides exposure. Grass and thin snow can
        // surround every trunk side without obscuring its reachable surface.
        if(item==log && !hasLeaves(world,pos))return false;
        return visibleLoaded(world,pos);
    }
    private boolean hasLeaves(ServerWorld world,BlockPos pos){
        for(BlockPos leaf:BlockPos.iterate(pos.add(-3,0,-3),pos.add(3,6,3)))
            if(world.isChunkLoaded(leaf) && world.getBlockState(leaf).isIn(BlockTags.LEAVES))return true;
        return false;
    }
    private boolean visibleLoaded(ServerWorld world,BlockPos pos){
        Vec3d eyes=npc.getEyePos(),end=Vec3d.ofCenter(pos),line=end.subtract(eyes);
        int steps=Math.max(1,(int)Math.ceil(line.length()));
        for(int i=0;i<=steps;i++)if(!world.isChunkLoaded(BlockPos.ofFloored(eyes.add(line.multiply((double)i/steps)))))return false;
        var hit=world.raycast(new RaycastContext(eyes,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private boolean gather(ServerWorld world,Item item,int depth){
        if(!npc.autoLoot){status="Ative a coleta de drops para obter "+label(item);return false;}
        avoidedDrops.entrySet().removeIf(entry->entry.getValue()<=npc.age);
        List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,npc.getBoundingBox().expand(32),
            drop->drop.isAlive() && drop.getStack().isOf(item) && !drop.cannotPickup()
                && !avoidedDrops.containsKey(drop.getUuid()) && npc.backpack.canInsert(drop.getStack()));
        if(!drops.isEmpty()){
            ItemEntity nearest=drops.stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).orElseThrow();
            double distance=npc.squaredDistanceTo(nearest);
            if(!nearest.getUuid().equals(dropTarget)){
                dropTarget=nearest.getUuid();bestDropDistance=distance;dropProgressAt=npc.age;
            }else if(distance<bestDropDistance-0.1){bestDropDistance=distance;dropProgressAt=npc.age;}
            // A visible drop behind an impassable wall is not a permanent
            // dependency. Retry later while still looking for real material.
            if(npc.age-dropProgressAt>160){
                if(avoidedDrops.size()>=64)avoidedDrops.clear();
                avoidedDrops.put(nearest.getUuid(),npc.age+200);dropTarget=null;
                npc.getNavigation().stop();status="Procurar outro material com acesso seguro";return false;
            }
            status="Recolher fisicamente "+label(item);
            if(!npc.survival().canCollect(nearest) && npc.age>=nextRoute){nextRoute=npc.age+10;WorkNavigation.moveToDrop(npc,nearest,1.0);}
            return false;
        }
        dropTarget=null;
        if(!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)){
            status="As regras do mundo impedem minerar materiais com drops reais";return false;
        }
        if(item==log && npc.autoEquip && !preparingAxe && !ResourcePlan.usable(npc,AxeItem.class)
                && (woodReceived || wornAxe())){
            // The first log is genuinely gathered by hand. When another log
            // is needed, this project owns its tool prerequisite instead of
            // yielding its ingredients to the unrelated starter-craft job.
            // The guard permits the axe recipe to gather its own missing logs
            // without recursing wood -> axe -> wood forever.
            axePending=true;prepareAxe(world);
            return false;
        }
        BlockState required=item==Items.RAW_IRON?Blocks.IRON_ORE.getDefaultState():Blocks.STONE.getDefaultState();
        if((item==Items.COBBLESTONE || item==Items.COAL || item==Items.RAW_IRON) && !suitablePick(required)){
            if(!npc.autoEquip){status="Equipe uma picareta adequada ou ative equipamento automático";return false;}
            Item replacement=item==Items.RAW_IRON?Items.STONE_PICKAXE:Items.WOODEN_PICKAXE;
            // A worn stack is still an item but cannot satisfy this mining
            // prerequisite. Require an additional actual pick before mining.
            if(!ensure(world,replacement,count(replacement)+1,depth+1))return false;
        }
        if(target!=null && (miningFor!=item || !safeTarget(world,target,item))){target=null;mining.reset();}
        if(target!=null){
            status="Obter "+label(item)+" em bloco natural acessível";
            double distance=npc.squaredDistanceTo(Vec3d.ofCenter(target));
            if(distance<bestTargetDistance-0.2){bestTargetDistance=distance;targetSince=npc.age;}
            if(npc.age-targetSince>220){npc.intelligence().memory().failed(target,world.getTime());target=null;mining.reset();return false;}
            if(!work.approach(target))return false;
            if(mining.tick(target)){
                if(item==log)woodHarvests++;
                target=null;scanFor=null;
            }
            return false;
        }
        if(npc.age<searchAt)return false;
        if(scanFor!=item || scanOrigin==null || scanOrigin.getSquaredDistance(npc.getBlockPos())>8*8){
            scanFor=item;scanOrigin=npc.getBlockPos().toImmutable();scanRadius=0;scanY=0;scanEdge=0;
        }
        for(int i=0;i<SCAN_PER_TICK;i++){
            if(scanRadius>SEARCH_RADIUS){
                scans++;scanRadius=0;scanY=0;scanEdge=0;searchAt=npc.age+40;
                status="Procurar "+label(item)+" exposto em terreno carregado";
                surveyStep(world,item);return false;
            }
            int radius=scanRadius,edge=scanEdge;
            int dx=0,dz=0;
            if(radius>0){
                if(edge<2*radius){dx=-radius+edge;dz=-radius;}
                else if(edge<4*radius){dx=radius;dz=-radius+(edge-2*radius);}
                else if(edge<6*radius){dx=radius-(edge-4*radius);dz=radius;}
                else {dx=-radius;dz=radius-(edge-6*radius);}
            }
            int dy=scanY==0?0:(scanY%2==1?-(scanY+1)/2:scanY/2);
            BlockPos candidate=scanOrigin.add(dx,dy,dz);
            if(++scanEdge>=Math.max(1,8*scanRadius)){scanEdge=0;if(++scanY>=25){scanY=0;scanRadius++;}}
            if(safeTarget(world,candidate,item)){
                target=candidate.toImmutable();miningFor=item;targetSince=npc.age;bestTargetDistance=Double.MAX_VALUE;
                npc.intelligence().memory().remember(item==Items.RAW_IRON?"iron":item==Items.COAL?"coal":item==log?"wood":"material",target,world.getTime(),24000);
                status="Encontrei "+label(item)+"; ir buscar";return false;
            }
        }
        status="Procurar "+label(item)+" em blocos naturais visíveis";return false;
    }
    private boolean wornAxe(){
        if(npc.getMainHandStack().getItem() instanceof AxeItem && ResourcePlan.worn(npc.getMainHandStack()))return true;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.getItem() instanceof AxeItem && ResourcePlan.worn(stack))return true;
        }
        return false;
    }
    private void surveyStep(ServerWorld world,Item item){
        int[][] offsets={{12,0},{0,12},{-12,0},{0,-12},{18,18},{-18,18},{-18,-18},{18,-18}};
        int[] offset=offsets[(scans-1)%offsets.length];
        for(int dy=2;dy>=-3;dy--){
            BlockPos feet=scanOrigin.add(offset[0],dy,offset[1]);
            if(world.isChunkLoaded(feet) && WorkNavigation.safeWorkFooting(npc,feet)
                    && world.isSpaceEmpty(npc,new Box(feet).stretch(0,0.8,0))){
                work.moveTo(feet);status="Procurar caminho para novos materiais do projeto";return;
            }
        }
    }
    public void writeNbt(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putString("Dimension",dimension);
        n.putString("Log",Registries.ITEM.getId(log).toString());n.putString("Planks",Registries.ITEM.getId(planks).toString());
        n.putInt("WoodHarvests",woodHarvests);
        n.putBoolean("AxePending",axePending);
        n.putBoolean("WoodReceived",woodReceived);
        if(furnace!=null)n.putLong("Furnace",furnace.asLong());if(bench!=null)n.putLong("Bench",bench.asLong());
        if(preferredFurnace!=null)n.putLong("PreferredFurnace",preferredFurnace.asLong());
        if(preferredBench!=null)n.putLong("PreferredBench",preferredBench.asLong());
        if(crafting!=null){n.putString("Crafting",Registries.ITEM.getId(crafting).toString());n.putInt("CraftTicks",craftTicks);}
        root.put("ProjectMaterials",n);
    }
    public void readNbt(NbtCompound root){
        cancel();furnace=null;bench=null;preferredFurnace=null;preferredBench=null;dimension="";
        if(!root.contains("ProjectMaterials"))return;
        NbtCompound n=root.getCompound("ProjectMaterials");dimension=n.getString("Dimension");
        woodHarvests=Math.max(0,Math.min(10000,n.getInt("WoodHarvests")));
        axePending=n.getBoolean("AxePending");
        woodReceived=n.getBoolean("WoodReceived");
        Identifier l=Identifier.tryParse(n.getString("Log")),p=Identifier.tryParse(n.getString("Planks"));
        if(l!=null && p!=null)selectWood(Registries.ITEM.get(l),Registries.ITEM.get(p));
        if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString())){dimension="";return;}
        if(n.contains("Furnace"))furnace=BlockPos.fromLong(n.getLong("Furnace"));
        if(n.contains("Bench"))bench=BlockPos.fromLong(n.getLong("Bench"));
        if(n.contains("PreferredFurnace"))preferredFurnace=BlockPos.fromLong(n.getLong("PreferredFurnace"));
        if(n.contains("PreferredBench"))preferredBench=BlockPos.fromLong(n.getLong("PreferredBench"));
        Identifier c=Identifier.tryParse(n.getString("Crafting"));
        if(c!=null && Registries.ITEM.get(c)!=Items.AIR){crafting=Registries.ITEM.get(c);craftTicks=Math.max(0,Math.min(CRAFT_TICKS-1,n.getInt("CraftTicks")));}
    }
}
