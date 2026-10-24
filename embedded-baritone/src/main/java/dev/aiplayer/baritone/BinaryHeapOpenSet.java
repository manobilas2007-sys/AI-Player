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

import java.util.Arrays;

/** Baritone's indexed binary heap (leijurv), adapted with deterministic tie
 * ordering, smaller initial allocation and no external open-set interface. */
final class BinaryHeapOpenSet {
    private PathNode[] array=new PathNode[16];
    private int size;
    boolean isEmpty(){return size==0;}
    void insert(PathNode node){
        if(node.isOpen())throw new IllegalStateException("node is already open");
        if(size>=array.length-1)array=Arrays.copyOf(array,array.length*2);
        node.heapPosition=++size;array[size]=node;update(node);
    }
    void update(PathNode node){
        int index=node.heapPosition;
        if(index<1 || index>size || array[index]!=node)throw new IllegalStateException("node is not open");
        while(index>1){
            int parent=index>>>1;
            if(compare(array[parent],node)<=0)break;
            array[index]=array[parent];array[index].heapPosition=index;
            index=parent;
        }
        array[index]=node;node.heapPosition=index;
    }
    PathNode removeLowest(){
        if(size==0)throw new IllegalStateException("empty heap");
        PathNode result=array[1],last=array[size];array[size--]=null;result.heapPosition=-1;
        if(size==0)return result;
        int index=1;
        while((index<<1)<=size){
            int child=index<<1;
            if(child<size && compare(array[child+1],array[child])<0)child++;
            if(compare(last,array[child])<=0)break;
            array[index]=array[child];array[index].heapPosition=index;index=child;
        }
        array[index]=last;last.heapPosition=index;return result;
    }
    private static int compare(PathNode a,PathNode b){
        int c=Double.compare(a.combinedCost,b.combinedCost);
        if(c==0)c=Double.compare(a.estimatedCostToGoal,b.estimatedCostToGoal);
        if(c==0)c=Integer.compare(a.position.x(),b.position.x());
        if(c==0)c=Integer.compare(a.position.y(),b.position.y());
        if(c==0)c=Integer.compare(a.position.z(),b.position.z());
        return c;
    }
}
