package dev.aiplayer.ai.tasks;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import java.util.*;

/** Survey a site's real columns before choosing a floor. X/Z never move.
 * A one-block contour retains the confirmed elevation; larger contours
 * compare excavation and paid fill instead of rejecting the whole site. */
public record SiteTerrainPlan(BlockPos floor,int clearTop) {
    public static SiteTerrainPlan survey(ServerWorld world,BlockPos requested,int width,int length,int headroom){
        var heights=new ArrayList<Integer>();int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int x=0;x<width;x++)for(int z=0;z<length;z++){
            BlockPos column=requested.add(x,0,z);
            if(!world.isChunkLoaded(column))return null;
            int top=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())-1;
            for(;top>world.getBottomY();top--){
                BlockPos p=new BlockPos(column.getX(),top,column.getZ());var s=world.getBlockState(p);
                if(!world.getFluidState(p).isEmpty())break;
                if(s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isOf(Blocks.SNOW))continue;
                if(s.isSolidBlock(world,p) || s.isOf(Blocks.FARMLAND))break;
            }
            heights.add(top+1);low=Math.min(low,top+1);high=Math.max(high,top+1);
        }
        int selected=requested.getY();
        if(high-low>1){
            double best=Double.POSITIVE_INFINITY;
            for(int y=low;y<=high && y<world.getTopY()-headroom-1;y++){
                double cost=Math.abs(y-requested.getY())*2;
                for(int terrain:heights)cost+=terrain>y?(terrain-y)*1.5:y-terrain;
                if(cost<best){best=cost;selected=y;}
            }
        }
        return new SiteTerrainPlan(new BlockPos(requested.getX(),selected,requested.getZ()),Math.max(selected+headroom-1,high));
    }
    public List<BlockPos> clearance(int width,int length,int frontMargin,int backMargin){
        var result=new ArrayList<BlockPos>();
        for(int y=clearTop;y>=floor.getY();y--)for(int x=0;x<width;x++)for(int z=-frontMargin;z<length+backMargin;z++)
            result.add(new BlockPos(floor.getX()+x,y,floor.getZ()+z));
        return result;
    }
}
