package dev.aiplayer.commands;

import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.ResourceGathering;
import dev.aiplayer.ai.tasks.CompanionTasks;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.WeakHashMap;

/** Adapter for ordinary owner chat. Pending dialogue changes no running work;
 * only a matching, fresh confirmation starts a terrain/crop project. */
public final class CompanionDialogue {
    private static final Map<CompanionEntity,OrderConversation> CONVERSATIONS=new WeakHashMap<>();
    private CompanionDialogue() {}

    public static boolean handle(String raw,ServerPlayerEntity sender,CompanionEntity npc){
        if(npc==null || sender==null || !npc.isAlive() || !npc.isOwner(sender)
                || sender.getWorld()!=npc.getWorld() || npc.getWorld().isClient
                || npc.squaredDistanceTo(sender)>48*48)return false;
        OrderConversation state;
        synchronized(CONVERSATIONS){
            if(CONVERSATIONS.size()>=128 && !CONVERSATIONS.containsKey(npc))
                CONVERSATIONS.remove(CONVERSATIONS.keySet().iterator().next());
            state=CONVERSATIONS.computeIfAbsent(npc,ignored->new OrderConversation());
        }
        state.currentOwner(sender.getUuid());
        BlockPos at=sender.getBlockPos();
        var reply=state.accept(raw,sender.getUuid(),sender.getWorld().getRegistryKey().getValue().toString(),
            sender.getWorld().getTime(),new OrderConversation.Location(at.getX(),at.getY(),at.getZ()),null);
        if(!reply.handled())return false;
        if(!reply.text().isBlank())sender.sendMessage(Text.literal("[AI Player] "+reply.text()),false);
        var command=reply.execute();
        if(command==null)return true;
        BlockPos origin=new BlockPos(command.origin().x(),command.origin().y(),command.origin().z());
        switch(command.action()){
            case BUILD_HOUSE -> npc.tasks().startAt(CompanionTasks.Kind.BUILD_HOUSE,origin,planks(command.wood()));
            case CREATE_FARM -> npc.tasks().startAt(CompanionTasks.Kind.CREATE_FARM,origin,null);
            case TEND_FARM -> npc.tasks().startAt(CompanionTasks.Kind.TEND_FARM,origin,null);
            case CANCEL -> {npc.tasks().cancel();npc.gathering().cancel();}
            case FOLLOW -> {npc.tasks().cancel();npc.gathering().cancel();npc.setMode(Mode.FOLLOW);}
            case STATUS -> sender.sendMessage(Text.literal("[AI Player] "+npc.intelligence().status()),false);
            case WOOD,STONE,FOOD,HUNT_FOOD -> {
                npc.tasks().cancel();
                npc.gathering().start(command.action()==PortugueseOrderParser.Action.WOOD?ResourceGathering.Kind.WOOD
                    :command.action()==PortugueseOrderParser.Action.STONE?ResourceGathering.Kind.STONE:ResourceGathering.Kind.FOOD,
                    command.amount(),true);
            }
            default -> { }
        }
        return true;
    }
    public static boolean awaiting(CompanionEntity npc,ServerPlayerEntity owner){
        OrderConversation conversation;
        synchronized(CONVERSATIONS){conversation=CONVERSATIONS.get(npc);}
        return conversation!=null && conversation.hasPending(owner.getUuid(),owner.getWorld().getRegistryKey().getValue().toString(),
            owner.getWorld().getTime());
    }
    private static Item planks(String species){
        if(species==null)return null;
        return switch(species){
            case "spruce"->Items.SPRUCE_PLANKS;case "birch"->Items.BIRCH_PLANKS;
            case "jungle"->Items.JUNGLE_PLANKS;case "acacia"->Items.ACACIA_PLANKS;
            case "dark_oak"->Items.DARK_OAK_PLANKS;case "mangrove"->Items.MANGROVE_PLANKS;
            case "cherry"->Items.CHERRY_PLANKS;default->Items.OAK_PLANKS;
        };
    }
}
