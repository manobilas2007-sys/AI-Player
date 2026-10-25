package dev.aiplayer.ai.tasks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed, survival-buildable starter cabin. The palette is selected once by the
 * project, so later inventory changes cannot introduce a second wood species. */
public final class HouseBlueprint {
    public record Palette(String id,Item log,Item planks,Item stairs,Item slab,Item door) {}
    public record Part(BlockPos position,BlockState state,Item item,boolean door) {}
    private static final List<Palette> PALETTES=List.of(
        wood("oak"),wood("spruce"),wood("birch"),wood("jungle"),wood("acacia"),
        wood("dark_oak"),wood("mangrove"),wood("cherry"));
    private HouseBlueprint() {}
    private static Item item(String name){return Registries.ITEM.get(new Identifier("minecraft",name));}
    private static Palette wood(String name){return new Palette(name,item(name+"_log"),item(name+"_planks"),
        item(name+"_stairs"),item(name+"_slab"),item(name+"_door"));}
    public static List<Palette> palettes(){return PALETTES;}
    public static Palette palette(String id){return PALETTES.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);}
    public static Palette palette(Item planks){return PALETTES.stream().filter(p->p.planks()==planks).findFirst().orElse(null);}
    private static BlockState block(Item item){return ((BlockItem)item).getBlock().getDefaultState();}
    private static void add(Map<BlockPos,Part> parts,BlockPos anchor,int x,int y,int z,BlockState state,Item item){
        BlockPos pos=anchor.add(x,y,z);parts.putIfAbsent(pos,new Part(pos,state,item,false));
    }
    private static BlockState stair(Palette p,Direction facing){return block(p.stairs()).with(StairsBlock.FACING,facing)
        .with(StairsBlock.HALF,BlockHalf.BOTTOM);}
    public static BlockPos furnace(BlockPos anchor){return anchor.add(1,1,5);}
    public static BlockPos workbench(BlockPos anchor){return anchor.add(3,1,5);}
    public static BlockPos door(BlockPos anchor){return anchor.add(2,1,0);}
    public static BlockPos exit(BlockPos anchor){return anchor.add(2,0,-2);}
    public static List<BlockPos> foundation(BlockPos anchor){
        List<BlockPos> result=new ArrayList<>();
        for(int x=0;x<5;x++)for(int z=0;z<7;z++)result.add(anchor.add(x,-1,z));
        result.add(anchor.add(2,-1,-1));result.add(anchor.add(2,-1,-2));return result;
    }
    public static List<BlockPos> clearance(BlockPos anchor){
        List<BlockPos> result=new ArrayList<>();
        // Clear top-down so higher terrain is never left floating over a cut.
        for(int y=4;y>=0;y--)for(int x=0;x<5;x++)for(int z=-1;z<=7;z++)result.add(anchor.add(x,y,z));
        for(int y=2;y>=0;y--)result.add(anchor.add(2,y,-2));
        return result;
    }
    public static List<Part> plan(BlockPos anchor,Palette p){
        Map<BlockPos,Part> parts=new LinkedHashMap<>();
        BlockState planks=block(p.planks());
        // Floor first; all subsequent navigation has real, paid-for support.
        for(int x=0;x<5;x++)for(int z=0;z<7;z++)add(parts,anchor,x,0,z,planks,p.planks());
        // A full palette block gives the existing work navigator a stable
        // entry/exit node. The shaped stairs remain on the pitched roof.
        add(parts,anchor,2,0,-1,planks,p.planks());
        for(int x:new int[]{0,4})for(int z:new int[]{0,6})for(int y=1;y<=2;y++)
            add(parts,anchor,x,y,z,block(p.log()).with(PillarBlock.AXIS,Direction.Axis.Y),p.log());
        // Front piers seed the higher roof rows without an interior ceiling
        // that would hide the roof from the worker's eyes.
        for(int x:new int[]{1,3})for(int y=1;y<=3;y++)add(parts,anchor,x,y,0,planks,p.planks());
        for(int x:new int[]{0,4})for(int z:new int[]{0,6})
            add(parts,anchor,x,3,z,stair(p,x==0?Direction.EAST:Direction.WEST),p.stairs());
        for(int x:new int[]{0,4}){
            add(parts,anchor,x,3,-1,stair(p,x==0?Direction.EAST:Direction.WEST),p.stairs());
            for(int z=1;z<=7;z++)if(z!=6)add(parts,anchor,x,3,z,stair(p,x==0?Direction.EAST:Direction.WEST),p.stairs());
        }
        for(int x:new int[]{1,3}){
            add(parts,anchor,x,4,0,stair(p,x==1?Direction.EAST:Direction.WEST),p.stairs());
            add(parts,anchor,x,4,-1,stair(p,x==1?Direction.EAST:Direction.WEST),p.stairs());
            for(int z=1;z<=7;z++)add(parts,anchor,x,4,z,stair(p,x==1?Direction.EAST:Direction.WEST),p.stairs());
        }
        BlockState ridge=block(p.slab()).with(SlabBlock.TYPE,SlabType.TOP);
        for(int z=-1;z<=7;z++)add(parts,anchor,2,4,z,ridge,p.slab());
        // Finish the gables and walls after the roof: the worker retains routes
        // across the floor until no overhead placement remains.
        for(int z:new int[]{0,6})for(int x=1;x<=3;x++)add(parts,anchor,x,3,z,planks,p.planks());
        for(int y=1;y<=2;y++)for(int x=0;x<5;x++)for(int z=0;z<7;z++){
            if(x!=0 && x!=4 && z!=0 && z!=6)continue;
            if(x==2 && z==0)continue;
            boolean window=y==2 && (((x==0 || x==4) && (z==2 || z==4)) || (x==2 && z==6));
            add(parts,anchor,x,y,z,window?Blocks.GLASS.getDefaultState():planks,window?Items.GLASS:p.planks());
        }
        add(parts,anchor,1,1,5,Blocks.FURNACE.getDefaultState().with(net.minecraft.block.FurnaceBlock.FACING,Direction.NORTH),Items.FURNACE);
        add(parts,anchor,3,1,5,Blocks.CRAFTING_TABLE.getDefaultState(),Items.CRAFTING_TABLE);
        BlockPos door=door(anchor);
        BlockState lower=block(p.door()).with(DoorBlock.FACING,Direction.NORTH).with(DoorBlock.HINGE,DoorHinge.LEFT)
            .with(DoorBlock.HALF,DoubleBlockHalf.LOWER).with(DoorBlock.OPEN,true);
        parts.put(door,new Part(door,lower,p.door(),true));
        return List.copyOf(parts.values());
    }
}
