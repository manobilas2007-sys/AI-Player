package dev.aiplayer.ai.brain;

import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Bounded, dimension-scoped working memory. Expiry uses world ticks, not entity age. */
public final class BrainMemory {
    public record Site(String kind, BlockPos pos, long expires, int failures) {}
    private final LinkedHashMap<String,Site> sites=new LinkedHashMap<>();
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private String dimension="";
    private String objective="", step="", suspended="", reason="";
    private long transitions, completed, failures;
    private static String key(String kind,BlockPos pos){return kind+":"+pos.asLong();}
    public void dimension(String id) {
        if(!dimension.isEmpty() && !dimension.equals(id))sites.clear();
        dimension=id;
    }
    public void remember(String kind,BlockPos pos,long now,long duration) {
        if(pos==null)return;
        String key=key(kind,pos);
        Site prior=sites.remove(key);
        sites.put(key,new Site(kind,pos.toImmutable(),now+duration,prior==null?0:prior.failures()));
        while(sites.size()>96)sites.remove(sites.keySet().iterator().next());
    }
    public void failed(BlockPos pos,long now) {
        Site previous=sites.get(key("blocked",pos));
        int count=previous==null?1:Math.min(6,previous.failures()+1);
        sites.put(key("blocked",pos),new Site("blocked",pos.toImmutable(),now+Math.min(2400,100L*count*count),count));
        while(sites.size()>96)sites.remove(sites.keySet().iterator().next());
        failures++;
    }
    public boolean blocked(BlockPos pos,long now) {
        Site site=sites.get(key("blocked",pos));
        return site!=null && site.expires()>now;
    }
    public Optional<BlockPos> nearest(String kind,BlockPos from,long now,int radius) {
        expire(now);
        return sites.values().stream().filter(s->s.kind().equals(kind)
            && !blocked(s.pos(),now) && s.pos().getSquaredDistance(from)<=radius*radius)
            .min(Comparator.comparingDouble(s->s.pos().getSquaredDistance(from))).map(Site::pos);
    }
    public void forget(String kind,BlockPos pos){sites.remove(key(kind,pos));}
    public void expire(long now){sites.entrySet().removeIf(e->e.getValue().expires()<=now);}
    public void plan(String objective,String step){this.objective=objective;this.step=step;}
    public String objective(){return objective;}
    public String step(){return step;}
    public String suspended(){return suspended;}
    public String reason(){return reason;}
    public long failures(){return failures;}
    public void transition(DecisionPolicy.Activity from,DecisionPolicy.Activity to,String why,boolean workActive) {
        if(from==to){reason=why;return;}
        if(workActive && (from==DecisionPolicy.Activity.GATHER || from==DecisionPolicy.Activity.CRAFT))
            suspended=objective+" / "+step;
        if(to==DecisionPolicy.Activity.GATHER || to==DecisionPolicy.Activity.CRAFT)suspended="";
        reason=why;transitions++;
        history.addLast(from+" -> "+to+": "+why);
        while(history.size()>12)history.removeFirst();
    }
    public void succeeded(String result){completed++;step=result;}
    public void write(NbtCompound root) {
        NbtCompound n=new NbtCompound();
        n.putInt("Version",1);n.putString("Dimension",dimension);n.putString("Objective",objective);
        n.putString("Step",step);n.putString("Suspended",suspended);n.putString("Reason",reason);
        n.putLong("Transitions",transitions);n.putLong("Completed",completed);n.putLong("Failures",failures);
        NbtList list=new NbtList();
        for(Site site:sites.values()) {
            NbtCompound s=new NbtCompound();s.putString("Kind",site.kind());s.putLong("Pos",site.pos().asLong());
            s.putLong("Expires",site.expires());s.putInt("Failures",site.failures());list.add(s);
        }
        n.put("Sites",list);root.put("Intelligence",n);
    }
    public void read(NbtCompound root) {
        sites.clear();history.clear();
        NbtCompound n=root.getCompound("Intelligence");
        dimension=n.getString("Dimension");objective=n.getString("Objective");step=n.getString("Step");
        suspended=n.getString("Suspended");reason=n.getString("Reason");
        transitions=n.getLong("Transitions");completed=n.getLong("Completed");failures=n.getLong("Failures");
        NbtList list=n.getList("Sites",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(96,list.size());i++){
            NbtCompound s=list.getCompound(i);BlockPos p=BlockPos.fromLong(s.getLong("Pos"));
            sites.put(key(s.getString("Kind"),p),new Site(s.getString("Kind"),p,s.getLong("Expires"),s.getInt("Failures")));
        }
    }
}
