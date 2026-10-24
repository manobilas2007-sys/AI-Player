package dev.aiplayer.baritone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Executable regression tests: no Minecraft instance or test framework needed. */
public final class CoreTest {
    private static int checks;
    private static final AStarPathFinder SEARCH=new AStarPathFinder();
    private static Position p(int x,int z){return new Position(x,0,z);}
    private static Step edge(Position to,double cost){return new Step(to,cost,Movement.TRAVERSE);}
    private static void expect(boolean condition,String message){
        checks++;if(!condition)throw new AssertionError(message);
    }
    private static void close(double actual,double expected,String message){expect(Math.abs(actual-expected)<1e-7,message+": "+actual+" != "+expected);}
    private static SearchContext graph(Map<Position,List<Step>> edges){return from->edges.getOrDefault(from,List.of());}
    public static void main(String[] args){
        alreadySatisfiedDoesNotTouchGraph();
        choosesCheaperRouteAndDecreasesOpenKey();
        reopensClosedNodeForBetterRoute();
        boundedResultsNeverPretendSuccess();
        adjacencyWorkIsAlsoBounded();
        diagonalCostAndGoalPredicates();
        fleeEveryThreatAtRequestedHeight();
        deterministicTiesAndImmutableResults();
        invalidValuesAreRejected();
        matchesDijkstraAcrossWeightedMaps();
        System.out.println(checks+" embedded Baritone core checks passed");
    }
    private static void alreadySatisfiedDoesNotTouchGraph(){
        Position start=p(0,0);
        SearchResult result=SEARCH.search(start,new GoalBlock(start),from->{throw new AssertionError("unexpected expansion");},new SearchLimits(16,8));
        expect(result.complete() && result.steps().isEmpty(),"already satisfied goal");
        expect(result.expandedNodes()==0,"already satisfied expanded nodes");close(result.cost(),0,"already satisfied cost");
    }
    private static void choosesCheaperRouteAndDecreasesOpenKey(){
        Position start=p(0,0),middle=p(0,1),goal=p(1,0);
        Map<Position,List<Step>> edges=Map.of(start,List.of(edge(goal,50),edge(middle,5)),middle,List.of(edge(goal,5)));
        Goal zero=zeroHeuristicGoal(goal);
        SearchResult result=SEARCH.search(start,zero,graph(edges),new SearchLimits(16,8));
        expect(result.complete(),"weighted graph complete");close(result.cost(),10,"decrease-key chose cheaper path");
        expect(result.steps().size()==2 && result.steps().get(0).destination().equals(middle),"weighted detour retained");
    }
    private static void reopensClosedNodeForBetterRoute(){
        Position start=p(0,0),a=p(1,0),b=p(0,1),goal=p(2,0);
        Goal inconsistentButAdmissible=new Goal(){
            public boolean isInGoal(int x,int y,int z){return new Position(x,y,z).equals(goal);}
            public double heuristic(int x,int y,int z){return new Position(x,y,z).equals(b)?50:0;}
        };
        Map<Position,List<Step>> edges=Map.of(start,List.of(edge(a,50),edge(b,5)),a,List.of(edge(goal,100)),b,List.of(edge(a,5)));
        SearchResult result=SEARCH.search(start,inconsistentButAdmissible,graph(edges),new SearchLimits(16,8));
        expect(result.complete(),"reopened graph complete");close(result.cost(),110,"closed node must reopen");
        expect(result.steps().size()==3 && result.steps().get(0).destination().equals(b),"reopened parent chain");
        expect(result.expandedNodes()==5,"closed node actually expanded twice");
    }
    private static void boundedResultsNeverPretendSuccess(){
        Position start=p(0,0);
        SearchContext line=from->List.of(edge(from.offset(1,0,0),5));
        SearchResult partial=SEARCH.search(start,new GoalBlock(20,0,0),line,new SearchLimits(3,64));
        expect(partial.status()==SearchResult.Status.PARTIAL && !partial.complete(),"node cap must not report complete");
        expect(partial.expandedNodes()<=3 && partial.steps().size()<=2,"node cap bounds retained progress");
        SearchResult radius=SEARCH.search(start,new GoalBlock(20,0,0),line,new SearchLimits(64,2));
        expect(!radius.complete() && radius.steps().stream().allMatch(step->step.destination().x()<=2),"distance bound");
        SearchResult noPath=SEARCH.search(start,new GoalBlock(3,0,0),from->List.of(),new SearchLimits(16,8));
        expect(noPath.status()==SearchResult.Status.NO_PATH && noPath.steps().isEmpty(),"disconnected graph");
        SearchResult limited=SEARCH.search(start,new GoalBlock(3,0,0),line,new SearchLimits(1,8));
        expect(limited.status()==SearchResult.Status.LIMIT && limited.steps().isEmpty(),"zero progress at budget");
        SearchResult zeroGoalClip=SEARCH.search(start,zeroHeuristicGoal(p(100,0)),line,new SearchLimits(4,2));
        expect(zeroGoalClip.status()==SearchResult.Status.LIMIT,"clipped graph with no heuristic progress");
    }
    private static void adjacencyWorkIsAlsoBounded(){
        int[] generated={0};
        SearchContext unbounded=from->()->new java.util.Iterator<Step>(){
            public boolean hasNext(){return true;}
            public Step next(){generated[0]++;return edge(from.offset(1,0,0),5);}
        };
        SearchResult result=SEARCH.search(p(0,0),new GoalBlock(100,0,0),unbounded,new SearchLimits(4,8));
        expect(!result.complete(),"unbounded adjacency must not invent success");
        expect(generated[0]<=257 && result.expandedNodes()<=4,"total considered-edge budget");
        SearchResult vertical=SEARCH.search(p(0,0),new GoalYLevel(20),
            from->List.of(new Step(from.offset(0,1,0),ActionCosts.JUMP_ONE_BLOCK_COST,Movement.ASCEND)),
            new SearchLimits(64,2));
        expect(!vertical.complete() && vertical.steps().stream().allMatch(step->step.destination().y()<=2),
            "radius must also bound vertical exploration");
    }
    private static void diagonalCostAndGoalPredicates(){
        double walk=ActionCosts.WALK_ONE_BLOCK_COST;
        close(new GoalBlock(1,0,1).heuristic(0,0,0),Math.sqrt(2)*walk,"octile heuristic");
        expect(new GoalBlock(1,1,0).heuristic(0,0,0)<=walk+ActionCosts.JUMP_ONE_BLOCK_COST,"simultaneous horizontal and vertical lower bound");
        GoalNear near=new GoalNear(new Position(0,0,0),2);
        expect(near.isInGoal(2,0,0) && !near.isInGoal(2,2,0),"spherical near membership");
        close(near.heuristic(1,1,0),0,"near heuristic must vanish inside goal");
        expect(near.heuristic(4,0,0)<=2*walk,"near lower bound");
        GoalXZ xz=new GoalXZ(1,2);expect(xz.isInGoal(1,100,2),"XZ ignores height");
        GoalYLevel y=new GoalYLevel(-20);expect(y.isInGoal(30,-20,-60),"Y-level ignores horizontal");
        Goal[] constituents={new GoalBlock(3,0,0),new GoalBlock(0,0,2)};
        GoalComposite composite=new GoalComposite(constituents);constituents[0]=new GoalBlock(99,0,0);
        expect(composite.isInGoal(3,0,0),"composite input defensive copy");
        composite.goals()[0]=new GoalBlock(100,0,0);expect(composite.isInGoal(3,0,0),"composite output defensive copy");
        close(composite.heuristic(0,0,0),2*walk,"composite minimum heuristic");
        expect(Double.isFinite(new GoalBlock(Integer.MIN_VALUE,0,0).heuristic(Integer.MAX_VALUE,0,0)),"coordinate subtraction must not overflow");
    }
    private static void fleeEveryThreatAtRequestedHeight(){
        GoalRunAway flee=new GoalRunAway(4,3,new Position(0,0,0),new Position(6,0,0));
        expect(!flee.isInGoal(4,3,0),"flee must satisfy all threats");
        expect(flee.isInGoal(3,3,4) && !flee.isInGoal(3,4,4),"flee optional maintained height");
        close(flee.heuristic(3,3,4),0,"flee heuristic at goal");
        expect(flee.heuristic(0,3,0)>0,"flee shortfall heuristic");
    }
    private static void deterministicTiesAndImmutableResults(){
        Position start=p(0,0),goal=p(1,1),a=p(0,1),b=p(1,0);
        Map<Position,List<Step>> edges=Map.of(start,List.of(edge(b,5),edge(a,5)),a,List.of(edge(goal,5)),b,List.of(edge(goal,5)));
        SearchResult first=SEARCH.search(start,zeroHeuristicGoal(goal),graph(edges),new SearchLimits(16,8));
        for(int i=0;i<30;i++)expect(first.equals(SEARCH.search(start,zeroHeuristicGoal(goal),graph(edges),new SearchLimits(16,8))),"deterministic equal costs");
        try {first.steps().add(edge(start,5));throw new AssertionError("mutable result");}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void invalidValuesAreRejected(){
        rejects(()->new Step(p(0,0),Double.NaN,Movement.TRAVERSE));
        rejects(()->new Step(p(0,0),0,Movement.TRAVERSE));
        rejects(()->new SearchLimits(0,8));rejects(()->new SearchLimits(8,0));
        rejects(()->new GoalNear(p(0,0),-1));rejects(()->new GoalComposite());rejects(()->new GoalRunAway(1));
        Goal invalid=new Goal(){public boolean isInGoal(int x,int y,int z){return false;}public double heuristic(int x,int y,int z){return Double.NaN;}};
        rejects(()->SEARCH.search(p(0,0),invalid,from->List.of(),new SearchLimits(8,8)));
    }
    /** Compare actual A* route costs against a separate exhaustive cost oracle. */
    private static void matchesDijkstraAcrossWeightedMaps(){
        for(int seed=0;seed<30;seed++){
            java.util.Random random=new java.util.Random(seed);
            Map<Position,List<Step>> edges=new HashMap<>();
            for(int x=0;x<5;x++)for(int z=0;z<5;z++){
                Position from=p(x,z);List<Step> steps=new ArrayList<>();
                for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    int nx=x+delta[0],nz=z+delta[1];
                    if(nx>=0 && nx<5 && nz>=0 && nz<5)steps.add(edge(p(nx,nz),5+random.nextInt(7)));
                }
                edges.put(from,List.copyOf(steps));
            }
            Position start=p(0,0),goal=p(4,4);
            SearchResult actual=SEARCH.search(start,new GoalBlock(goal),graph(edges),new SearchLimits(256,16));
            expect(actual.complete(),"weighted map reachable");close(actual.cost(),oracle(start,goal,edges),"A* optimality seed "+seed);
            double summed=actual.steps().stream().mapToDouble(Step::cost).sum();close(actual.cost(),summed,"cost equals physical edge sequence");
        }
    }
    private static double oracle(Position start,Position goal,Map<Position,List<Step>> edges){
        Map<Position,Double> distance=new HashMap<>();distance.put(start,0.0);
        for(int pass=0;pass<edges.size();pass++){
            boolean changed=false;
            for(var entry:edges.entrySet())for(Step edge:entry.getValue()){
                double proposed=distance.getOrDefault(entry.getKey(),Double.POSITIVE_INFINITY)+edge.cost();
                if(proposed<distance.getOrDefault(edge.destination(),Double.POSITIVE_INFINITY)){
                    distance.put(edge.destination(),proposed);changed=true;
                }
            }
            if(!changed)break;
        }
        return distance.getOrDefault(goal,Double.POSITIVE_INFINITY);
    }
    private static Goal zeroHeuristicGoal(Position target){
        return new Goal(){public boolean isInGoal(int x,int y,int z){return target.equals(new Position(x,y,z));}public double heuristic(int x,int y,int z){return 0;}};
    }
    private static void rejects(Runnable operation){
        try {operation.run();throw new AssertionError("invalid value accepted");}catch(IllegalArgumentException expected){checks++;}
    }
}
