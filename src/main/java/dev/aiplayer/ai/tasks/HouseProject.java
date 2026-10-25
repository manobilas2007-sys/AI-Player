package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.List;

/** A persisted physical building process. The dialogue chooses a location and
 * palette; preparation and construction use the same real resources as players. */
public final class HouseProject {
    private enum Phase { SURVEY, CLEAR, FOUNDATION, BUILD, EXIT, DONE }
    private final CompanionEntity npc;
    private final ProjectWork work;
    private final ProjectMaterials materials;
    private boolean active;
    private BlockPos anchor;
    private HouseBlueprint.Palette palette;
    private Phase phase=Phase.DONE;
    private int cursor,placed;
    private String dimension="",status="Sem abrigo em construção";

    public HouseProject(CompanionEntity npc,ProjectWork work,ProjectMaterials materials){
        this.npc=npc;this.work=work;this.materials=materials;
    }
    public boolean active(){return active;}
    public int progress(){return placed;}
    public String status(){return status;}
    public BlockPos anchor(){return anchor;}
    public Item selectedPlanks(){return palette==null?Items.AIR:palette.planks();}
    public String diagnostics(){return "house="+phase+" index="+cursor+" placed="+placed+" anchor="+anchor
        +" palette="+(palette==null?"none":palette.id())+" pos="+npc.getPos()+" work="+work.status();}
    public void start(BlockPos origin,Item preferredPlanks){
        cancel();
        if(!(npc.getWorld() instanceof ServerWorld) || origin==null)return;
        palette=preferredPlanks==null?choosePalette():HouseBlueprint.palette(preferredPlanks);
        if(palette==null){status="Não reconheci essa madeira para o abrigo";return;}
        anchor=origin.add(2,0,0).toImmutable();
        dimension=npc.getWorld().getRegistryKey().getValue().toString();
        active=true;phase=Phase.SURVEY;cursor=0;placed=0;
        materials.selectWood(palette.log(),palette.planks());
        reserveTerrain();
        status="Conferir terreno do abrigo de "+palette.id();
    }
    private void reserveTerrain(){
        if(anchor!=null && npc.getWorld() instanceof ServerWorld world)
            materials.keepOut(new Box(anchor.getX(),world.getBottomY(),anchor.getZ()-2,
                anchor.getX()+5,world.getTopY(),anchor.getZ()+8));
    }
    private HouseBlueprint.Palette choosePalette(){
        HouseBlueprint.Palette best=HouseBlueprint.palette("oak");int score=-1;
        for(var p:HouseBlueprint.palettes()){
            int value=npc.backpack.count(p.planks())+npc.backpack.count(p.log())*4;
            if(npc.getMainHandStack().isOf(p.planks()))value+=npc.getMainHandStack().getCount();
            if(npc.getMainHandStack().isOf(p.log()))value+=npc.getMainHandStack().getCount()*4;
            if(value>score){best=p;score=value;}
        }
        if(score==0 && npc.getWorld() instanceof ServerWorld world){
            double distance=Double.MAX_VALUE;
            // Choose from nearby visible natural timber when the backpack has
            // no palette yet, rather than demanding oak in every biome.
            for(BlockPos pos:BlockPos.iterate(npc.getBlockPos().add(-12,-3,-12),npc.getBlockPos().add(12,5,12))){
                if(!world.isChunkLoaded(pos) || !world.isChunkLoaded(pos.down())
                        || !world.getBlockState(pos.down()).isIn(BlockTags.DIRT)
                        || protectedAt(world,pos))continue;
                BlockState state=world.getBlockState(pos);
                if(!state.isIn(BlockTags.LOGS))continue;
                var hit=world.raycast(new net.minecraft.world.RaycastContext(npc.getEyePos(),Vec3d.ofCenter(pos),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,npc));
                if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS && !hit.getBlockPos().equals(pos))continue;
                double at=npc.squaredDistanceTo(Vec3d.ofCenter(pos));
                if(at>=distance)continue;
                for(var candidate:HouseBlueprint.palettes())if(state.getBlock().asItem()==candidate.log()){
                    best=candidate;distance=at;break;
                }
            }
        }
        return best;
    }
    public void pause(){work.pause();materials.pause();npc.clearWorkingVisual();}
    public void cancel(){
        active=false;anchor=null;palette=null;phase=Phase.DONE;cursor=0;placed=0;
        work.cancel();materials.cancel();status="Construção cancelada; blocos concluídos preservados";
    }
    private boolean own(ServerWorld world,BlockPos pos){return OwnedBlocks.get(world).owned(pos,npc.getUuid());}
    private boolean protectedAt(ServerWorld world,BlockPos pos){
        var ledger=OwnedBlocks.get(world);
        return ledger.protectedBlock(pos) || (ledger.claimed(pos) && !ledger.owned(pos,npc.getUuid()));
    }
    private boolean soft(BlockState state){
        return state.isOf(Blocks.SNOW) || state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH)
            || state.isOf(Blocks.VINE) || state.isIn(BlockTags.FLOWERS)
            || (state.isIn(BlockTags.LEAVES) && (!state.contains(LeavesBlock.PERSISTENT) || !state.get(LeavesBlock.PERSISTENT)));
    }
    private boolean naturalLog(ServerWorld world,BlockPos pos){
        // A log is an obstruction only when it belongs to a nearby natural
        // canopy. Untracked decorative beams are not automatically demolished.
        for(int dy=-1;dy<=12;dy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            BlockPos leaf=pos.add(dx,dy,dz);
            if(!world.isChunkLoaded(leaf))continue;
            BlockState s=world.getBlockState(leaf);
            if(s.isIn(BlockTags.LEAVES) && (!s.contains(LeavesBlock.PERSISTENT) || !s.get(LeavesBlock.PERSISTENT)))return true;
        }
        return false;
    }
    private boolean terrain(ServerWorld world,BlockPos pos,BlockState state){
        return soft(state) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK)
            || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM)
            || state.isOf(Blocks.ROOTED_DIRT) || state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE)
            || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.GRANITE)
            || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)
            || state.isOf(Blocks.CLAY) || state.isOf(Blocks.SNOW_BLOCK)
            || (state.isIn(BlockTags.LOGS) && naturalLog(world,pos));
    }
    private boolean loaded(ServerWorld world,BlockPos pos){
        if(world.isChunkLoaded(pos))return true;
        status="Aguardar terreno carregado para o abrigo";return false;
    }
    private boolean checkClearCell(ServerWorld world,BlockPos pos){
        if(!loaded(world,pos))return false;
        BlockState state=world.getBlockState(pos);
        if(protectedAt(world,pos) || world.getBlockEntity(pos)!=null){
            status="Obra pausada: preservar construção existente em "+pos.toShortString();return false;
        }
        if(!world.getFluidState(pos).isEmpty()){
            status="Esse ponto tem água ou lava; preciso de outro local para o abrigo";return false;
        }
        if(!state.isAir() && !terrain(world,pos,state)){
            status="Obra pausada: obstáculo existente não é terreno natural em "+pos.toShortString();return false;
        }
        return true;
    }
    private BlockPos foundationGap(ServerWorld world,BlockPos top){
        BlockPos support=null;
        for(int depth=0;depth<4;depth++){
            BlockPos pos=top.down(depth);
            if(!loaded(world,pos))return top;
            if(protectedAt(world,pos) && !world.getBlockState(pos).isSolidBlock(world,pos)){
                status="Preservar bloco protegido na fundação";return top;
            }
            if(!world.getFluidState(pos).isEmpty() || world.getBlockEntity(pos)!=null){
                status="A fundação precisa de terreno seco sem blocos de armazenamento";return top;
            }
            if(world.getBlockState(pos).isSolidBlock(world,pos)){support=pos;break;}
            if(!world.getBlockState(pos).isAir()){
                status="Obstáculo não removível na fundação";return top;
            }
        }
        if(support==null){status="Desnível maior que quatro blocos; preciso de outro ponto para o abrigo";return top;}
        return support.equals(top)?null:support.up();
    }
    private void survey(ServerWorld world){
        for(BlockPos pos:HouseBlueprint.clearance(anchor))if(!checkClearCell(world,pos))return;
        for(BlockPos top:HouseBlueprint.foundation(anchor)){
            BlockPos gap=foundationGap(world,top);
            if(gap!=null && (!world.getBlockState(gap).isAir() || gap.equals(top)
                    && !hasFoundationSupport(world,top)))return;
        }
        phase=Phase.CLEAR;cursor=0;status="Preparar terreno: retirar neve, vegetação e desníveis naturais";
    }
    private boolean hasFoundationSupport(ServerWorld world,BlockPos top){
        for(int depth=0;depth<4;depth++){
            BlockPos pos=top.down(depth);
            if(!world.isChunkLoaded(pos) || !world.getFluidState(pos).isEmpty() || world.getBlockEntity(pos)!=null)return false;
            if(world.getBlockState(pos).isSolidBlock(world,pos))return true;
            if(!world.getBlockState(pos).isAir())return false;
        }
        return false;
    }
    private void clear(ServerWorld world){
        List<BlockPos> cells=HouseBlueprint.clearance(anchor);
        while(cursor<cells.size() && world.isChunkLoaded(cells.get(cursor)) && world.getBlockState(cells.get(cursor)).isAir())cursor++;
        if(cursor>=cells.size()){phase=Phase.FOUNDATION;cursor=0;return;}
        BlockPos pos=cells.get(cursor);
        if(!checkClearCell(world,pos))return;
        BlockState state=world.getBlockState(pos);
        if(!world.getOtherEntities(npc,new Box(pos.up()).stretch(0,1,0),e->e instanceof LivingEntity && e.isAlive()).isEmpty()){
            status="Aguardar passagem livre para preparar o terreno";return;
        }
        if(state.isToolRequired() && !ResourcePlan.usable(npc,PickaxeItem.class)){
            if(!materials.ensure(Items.STONE_PICKAXE,1)){status=materials.status();return;}
        }
        status="Preparar terreno: "+cursor+"/"+cells.size();
        boolean removed=soft(state)?work.clearNatural(pos):work.safeMine(pos);
        if(removed && world.getBlockState(pos).isAir())cursor++;
    }
    private void foundations(ServerWorld world){
        List<BlockPos> tops=HouseBlueprint.foundation(anchor);
        while(cursor<tops.size()){
            BlockPos top=tops.get(cursor);
            if(!loaded(world,top))return;
            if(world.getBlockState(top).isSolidBlock(world,top)){cursor++;continue;}
            if(!hasFoundationSupport(world,top)){
                status="Aguardar fundação seca e segura até quatro blocos abaixo";return;
            }
            BlockPos gap=foundationGap(world,top);
            if(gap==null){cursor++;continue;}
            if(!world.getBlockState(gap).isAir() || protectedAt(world,gap))return;
            if(!materials.ensure(Items.COBBLESTONE,1)){status=materials.status();return;}
            status="Firmar fundação real do abrigo";
            work.place(gap,Blocks.COBBLESTONE.getDefaultState(),Items.COBBLESTONE,1);return;
        }
        phase=Phase.BUILD;cursor=0;status="Construir abrigo com uma única madeira";
    }
    private boolean matches(ServerWorld world,HouseBlueprint.Part part){
        if(!own(world,part.position()))return false;
        BlockState actual=world.getBlockState(part.position());
        if(!actual.isOf(part.state().getBlock()))return false;
        if(actual.getBlock() instanceof net.minecraft.block.StairsBlock
                && (actual.get(net.minecraft.block.StairsBlock.FACING)!=part.state().get(net.minecraft.block.StairsBlock.FACING)
                    || actual.get(net.minecraft.block.StairsBlock.HALF)!=part.state().get(net.minecraft.block.StairsBlock.HALF)))return false;
        if(actual.getBlock() instanceof net.minecraft.block.SlabBlock
                && actual.get(net.minecraft.block.SlabBlock.TYPE)!=part.state().get(net.minecraft.block.SlabBlock.TYPE))return false;
        if(actual.getBlock() instanceof net.minecraft.block.PillarBlock
                && actual.get(net.minecraft.block.PillarBlock.AXIS)!=part.state().get(net.minecraft.block.PillarBlock.AXIS))return false;
        if(!part.door())return true;
        BlockState upper=world.getBlockState(part.position().up());
        return upper.isOf(part.state().getBlock()) && own(world,part.position().up())
            && actual.get(DoorBlock.HALF)==DoubleBlockHalf.LOWER && upper.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER;
    }
    private void build(ServerWorld world){
        List<HouseBlueprint.Part> plan=HouseBlueprint.plan(anchor,palette);
        while(cursor<plan.size() && matches(world,plan.get(cursor)))cursor++;
        placed=cursor;
        if(cursor>=plan.size()){phase=Phase.EXIT;cursor=0;return;}
        var part=plan.get(cursor);
        if(!loaded(world,part.position()))return;
        if(protectedAt(world,part.position()) || (!world.getBlockState(part.position()).isAir() && !own(world,part.position()))){
            status="Preservar bloco que apareceu durante a obra em "+part.position().toShortString();return;
        }
        // The final furnace may already have been built for glass production.
        if(cursor>=35){
            materials.preferFurnace(HouseBlueprint.furnace(anchor));
            materials.preferWorkbench(HouseBlueprint.workbench(anchor));
        }
        if(!materials.ensure(part.item(),1)){status=materials.status();return;}
        status="Construir abrigo de "+palette.id()+": "+cursor+"/"+plan.size();
        boolean result=part.door()?work.placeDoor(part.position(),part.state(),part.item())
            :work.place(part.position(),part.state(),part.item(),1);
        if(result && matches(world,part)){cursor++;placed=cursor;}
    }
    private void exit(ServerWorld world){
        BlockPos outside=HouseBlueprint.exit(anchor);
        if(npc.squaredDistanceTo(Vec3d.ofBottomCenter(outside))>0.8*0.8){
            status="Sair do abrigo pela porta";work.moveTo(outside);return;
        }
        BlockPos door=HouseBlueprint.door(anchor);
        BlockState lower=world.getBlockState(door),upper=world.getBlockState(door.up());
        if(own(world,door) && own(world,door.up()) && lower.getBlock() instanceof DoorBlock
                && upper.isOf(lower.getBlock()) && work.approach(door)){
            if(lower.get(DoorBlock.OPEN)){
                world.setBlockState(door,lower.with(DoorBlock.OPEN,false),3);
                world.setBlockState(door.up(),upper.with(DoorBlock.OPEN,false),3);
                npc.animateInteraction(CompanionEntity.PLACE,5);npc.swingHand(Hand.MAIN_HAND);
            }
        }
        active=false;phase=Phase.DONE;status="Abrigo concluído: porta, janelas, telhado, bancada e fornalha";
        npc.getNavigation().stop();npc.intelligence().memory().succeeded(status);
    }
    public void tick(){
        if(!active || anchor==null || palette==null || !(npc.getWorld() instanceof ServerWorld world))return;
        if(!dimension.equals(world.getRegistryKey().getValue().toString())){cancel();status="Construção cancelada: dimensão alterada";return;}
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return;}
        if(!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)){
            status="As regras do mundo impedem construir e recolher materiais";return;
        }
        switch(phase){case SURVEY->survey(world);case CLEAR->clear(world);case FOUNDATION->foundations(world);
            case BUILD->build(world);case EXIT->exit(world);case DONE->{active=false;}}
    }
    public void writeNbt(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putInt("Version",1);n.putBoolean("Active",active);
        n.putString("Dimension",dimension);n.putString("Phase",phase.name());n.putInt("Index",cursor);n.putInt("Placed",placed);
        if(anchor!=null)n.putLong("Anchor",anchor.asLong());if(palette!=null)n.putString("Wood",palette.id());
        n.putString("Status",status);root.put("DetailedHouse",n);
    }
    public void readNbt(NbtCompound root){
        active=false;anchor=null;palette=null;phase=Phase.DONE;cursor=0;placed=0;
        if(!root.contains("DetailedHouse"))return;
        NbtCompound n=root.getCompound("DetailedHouse");
        if(!n.getBoolean("Active") || n.getInt("Version")!=1)return;
        dimension=n.getString("Dimension");
        if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString()))return;
        palette=HouseBlueprint.palette(n.getString("Wood"));if(palette==null || !n.contains("Anchor"))return;
        anchor=BlockPos.fromLong(n.getLong("Anchor"));
        if(anchor.getSquaredDistance(npc.getBlockPos())>128*128){status="Abrigo distante; aguardar nova ordem";return;}
        try{phase=Phase.valueOf(n.getString("Phase"));}catch(IllegalArgumentException invalid){return;}
        cursor=Math.max(0,Math.min(512,n.getInt("Index")));placed=Math.max(0,Math.min(512,n.getInt("Placed")));
        status=n.getString("Status");active=phase!=Phase.DONE;
        materials.selectWood(palette.log(),palette.planks());
        reserveTerrain();
    }
}
