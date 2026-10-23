package dev.aiplayer;

import dev.aiplayer.entity.CompanionEntity;
import dev.aiplayer.screen.CompanionMenu;
import dev.aiplayer.mixin.MobGoalsAccess;
import dev.aiplayer.commands.PortugueseOrderParser;
import dev.aiplayer.ai.ResourceGathering;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;
import java.util.Comparator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class AIPlayer implements ModInitializer {
    public static final EntityType<CompanionEntity> COMPANION = Registry.register(Registries.ENTITY_TYPE,
        id("companion"), FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CompanionEntity::new)
        .dimensions(EntityDimensions.fixed(0.6f, 1.8f)).trackRangeBlocks(64).build());
    public static final ScreenHandlerType<CompanionMenu> MENU = Registry.register(Registries.SCREEN_HANDLER,
        id("companion"), new ExtendedScreenHandlerType<>(CompanionMenu::new));
    public static final Item EGG = Registry.register(Registries.ITEM, id("companion_spawn_egg"),
        new SpawnEggItem(COMPANION, 0x32475b, 0x33c7d9, new Item.Settings()));
    public static Identifier id(String path) { return new Identifier("aiplayer", path); }
    @Override public void onInitialize() {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,blockEntity)->{
            if(world instanceof net.minecraft.server.world.ServerWorld server)dev.aiplayer.ai.OwnedBlocks.get(server).playerBroke(pos);
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("aiplayer")
                .then(net.minecraft.server.command.CommandManager.literal("falas")
                    .then(net.minecraft.server.command.CommandManager.literal("ligar").executes(context->setSpeech(context.getSource(),true)))
                    .then(net.minecraft.server.command.CommandManager.literal("desligar").executes(context->setSpeech(context.getSource(),false))))
                .then(net.minecraft.server.command.CommandManager.literal("status").executes(context->{
                    var player=context.getSource().getPlayerOrThrow();
                    var list=player.getWorld().getEntitiesByClass(CompanionEntity.class,player.getBoundingBox().expand(48),e->e.isOwner(player));
                    list.stream().min(Comparator.comparingDouble(player::squaredDistanceTo))
                        .ifPresent(npc->player.sendMessage(Text.literal("[AI Player] "+npc.intelligence().status()),false));
                    return list.isEmpty()?0:1;
                }))));

        FabricDefaultAttributeRegistry.register(COMPANION, CompanionEntity.attributes());
        // Offline Portuguese commands sent as ordinary chat; no API key or remote AI required.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, parameters) -> {
            var order=PortugueseOrderParser.parse(message.getContent().getString());
            if(order.action()==PortugueseOrderParser.Action.NONE)return;
            CompanionEntity npc=sender.getWorld().getEntitiesByClass(CompanionEntity.class,
                    sender.getBoundingBox().expand(48),c->c.isAlive() && c.isOwner(sender))
                    .stream().min(Comparator.comparingDouble(sender::squaredDistanceTo)).orElse(null);
            if(npc==null){sender.sendMessage(Text.literal("[AI Player] Seu NPC precisa estar a até 48 blocos para ouvir."),false);return;}
            if(order.action()==PortugueseOrderParser.Action.CANCEL)npc.gathering().cancel();
            else npc.gathering().start(order.action()==PortugueseOrderParser.Action.WOOD
                    ? ResourceGathering.Kind.WOOD : order.action()==PortugueseOrderParser.Action.STONE ? ResourceGathering.Kind.STONE : ResourceGathering.Kind.FOOD,order.amount(),true);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(EGG));
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!world.isClient && !player.isSpectator() && target instanceof LivingEntity mob) {
                for (CompanionEntity companion : world.getEntitiesByClass(CompanionEntity.class,
                        player.getBoundingBox().expand(32), c -> c.isAlive() && c.isOwner(player))) {
                    companion.markOwnerAttack(mob);
                }
            }
            return ActionResult.PASS; // Never cancel the player's actual hit.
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Neutral mobs keep their vanilla provocation rules.
            if (entity instanceof MobEntity mob
                && (mob instanceof HostileEntity || mob instanceof SlimeEntity)
                && !(mob instanceof EndermanEntity) && !(mob instanceof ZombifiedPiglinEntity)) {
                ((MobGoalsAccess)mob).aiplayer$getTargetSelector().add(3,
                    new ActiveTargetGoal<>(mob, CompanionEntity.class, 10, true, false, c -> c.isAlive()));
            }
        });
    }
    private static int setSpeech(net.minecraft.server.command.ServerCommandSource source,boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrThrow();
        var npc=player.getWorld().getEntitiesByClass(CompanionEntity.class,player.getBoundingBox().expand(48),
            e->e.isAlive() && e.isOwner(player)).stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        if(npc==null){player.sendMessage(Text.literal("[AI Player] Seu companheiro precisa estar a até 48 blocos."),false);return 0;}
        npc.speech().enabled(enabled);
        player.sendMessage(Text.literal(enabled?"[AI Player] Pronto, meu rei. Vou lhe avisar das coisas por aqui.":"[AI Player] Tá certo. Vou ficar mais na minha; continuo respondendo suas ordens."),false);
        return 1;
    }
}
