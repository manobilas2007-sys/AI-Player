/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

// AI-Player adaptation, 2026: Java 17, server-neutral API; see UPSTREAM.md.

package dev.aiplayer.baritone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-neutral adaptation of Baritone 1.10.5 AStarPathFinder and
 * AbstractNodeCostSearch (leijurv). Retains indexed decrease-key, reopening,
 * minimum-improvement relaxation and the best-so-far coefficient strategy.
 * Client settings, wall-clock timeouts, chunks and movement construction are
 * replaced by deterministic explicit bounds and an actor-supplied graph. */
public final class AStarPathFinder {
    private static final double MIN_IMPROVEMENT=0.01;
    private static final double[] COEFFICIENTS={1.5,2,2.5,3,4,5,10};

    public SearchResult search(Position start,Goal goal,SearchContext graph,SearchLimits limits){
        Objects.requireNonNull(start,"start");Objects.requireNonNull(goal,"goal");
        Objects.requireNonNull(graph,"graph");Objects.requireNonNull(limits,"limits");
        if(goal.isInGoal(start))return new SearchResult(SearchResult.Status.COMPLETE,List.of(),0,0);
        Map<Position,PathNode> nodes=new HashMap<>();
        BinaryHeapOpenSet open=new BinaryHeapOpenSet();
        PathNode first=new PathNode(start,goal);first.cost=0;first.combinedCost=first.estimatedCostToGoal;
        nodes.put(start,first);open.insert(first);
        PathNode[] best=new PathNode[COEFFICIENTS.length];
        java.util.Arrays.fill(best,first);
        int expanded=0;
        long considered=0,maxConsidered=Math.max(64L,(long)limits.maxNodes()*64);
        boolean limited=false;
        search: while(!open.isEmpty()){
            if(expanded>=limits.maxNodes()){limited=true;break;}
            PathNode current=open.removeLowest();expanded++;
            if(goal.isInGoal(current.position))return result(SearchResult.Status.COMPLETE,current,expanded);
            Iterable<Step> neighbors=Objects.requireNonNull(graph.neighbors(current.position),"neighbors");
            for(Step step:neighbors){
                if(++considered>maxConsidered){limited=true;break search;}
                Objects.requireNonNull(step,"step");
                if(step.cost()>=ActionCosts.COST_INF)continue;
                Position position=step.destination();
                if(!within(start,position,limits.maxDistance())){limited=true;continue;}
                double tentative=current.cost+step.cost();
                if(!Double.isFinite(tentative) || tentative>=ActionCosts.COST_INF)continue;
                PathNode neighbor=nodes.get(position);
                if(neighbor==null){
                    if(nodes.size()>=limits.maxNodes()){limited=true;continue;}
                    neighbor=new PathNode(position,goal);nodes.put(position,neighbor);
                }
                if(neighbor.cost-tentative<=MIN_IMPROVEMENT)continue;
                neighbor.previous=current;neighbor.incoming=step;neighbor.cost=tentative;
                neighbor.combinedCost=tentative+neighbor.estimatedCostToGoal;
                if(neighbor.isOpen())open.update(neighbor);else open.insert(neighbor);
                for(int i=0;i<COEFFICIENTS.length;i++){
                    if(metric(neighbor,COEFFICIENTS[i])+MIN_IMPROVEMENT<metric(best[i],COEFFICIENTS[i]))best[i]=neighbor;
                }
            }
        }
        // Like upstream, prefer inexpensive meaningful progress at successively
        // more permissive cost/heuristic ratios. Never call a partial path complete.
        for(PathNode candidate:best){
            if(candidate.previous!=null && candidate.estimatedCostToGoal+MIN_IMPROVEMENT<first.estimatedCostToGoal)
                return result(SearchResult.Status.PARTIAL,candidate,expanded);
        }
        return new SearchResult(limited?SearchResult.Status.LIMIT:SearchResult.Status.NO_PATH,List.of(),expanded,0);
    }
    private static boolean within(Position start,Position position,int radius){
        return Math.abs((long)position.x()-start.x())<=radius
            && Math.abs((long)position.y()-start.y())<=radius
            && Math.abs((long)position.z()-start.z())<=radius;
    }
    private static double metric(PathNode node,double coefficient){return node.estimatedCostToGoal+node.cost/coefficient;}
    private static SearchResult result(SearchResult.Status status,PathNode node,int expanded){
        List<Step> steps=new ArrayList<>();
        for(PathNode cursor=node;cursor.previous!=null;cursor=cursor.previous)steps.add(cursor.incoming);
        Collections.reverse(steps);
        return new SearchResult(status,steps,expanded,node.cost);
    }
}
