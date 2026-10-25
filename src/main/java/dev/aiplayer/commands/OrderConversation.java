package dev.aiplayer.commands;

import java.util.Objects;
import java.util.UUID;

import static dev.aiplayer.commands.PortugueseOrderParser.Action;

/** Small local dialogue state, independent of Minecraft and scoped to one NPC.
 * A confirmation authorizes exactly the original location, owner and dimension. */
public final class OrderConversation {
    public static final long CONFIRMATION_TICKS=1200;
    public record Location(int x,int y,int z) {}
    public record Command(Action action,int amount,Location origin,String wood) {}
    public record Reply(boolean handled,String text,Command execute) {
        private static Reply ignored(){return new Reply(false,"",null);}
        private static Reply say(String text){return new Reply(true,text,null);}
    }
    private enum Stage { LAYOUT, FARM_KIND, CONFIRM }
    private record Pending(UUID owner,String dimension,long created,long expires,Stage stage,Command command) {}
    private Pending pending;

    public boolean hasPending(UUID owner,String dimension,long now){
        return pending!=null && pending.owner().equals(owner) && pending.dimension().equals(dimension)
            && now>=pending.created() && now<=pending.expires();
    }
    public void clear(){pending=null;}
    /** Called only by an adapter that has already verified the NPC's owner. */
    public void currentOwner(UUID owner){if(pending!=null && !pending.owner().equals(owner))pending=null;}

    public Reply accept(String raw,UUID owner,String dimension,long now,Location location,String suggestedWood){
        Objects.requireNonNull(owner,"owner");Objects.requireNonNull(dimension,"dimension");
        Objects.requireNonNull(location,"location");
        if(raw==null || raw.isBlank() || raw.length()>512)return Reply.ignored();
        String text=PortugueseOrderParser.normalize(raw);
        if(pending!=null && !pending.owner().equals(owner))return Reply.ignored();
        boolean answer=affirmative(text) || negative(text) || shelterChoice(text) || farmChoice(text)!=Action.NONE
            || PortugueseOrderParser.woodSpecies(raw)!=null;
        if(pending!=null && (!pending.dimension().equals(dimension) || now<pending.created() || now>pending.expires())){
            pending=null;
            if(answer)return Reply.say("Essa confirmação venceu ou mudou de dimensão. Me diga a tarefa novamente.");
        }
        if(pending!=null && negative(text)){
            pending=null;return Reply.say("Tudo bem, descartei essa proposta. O trabalho atual continua como estava.");
        }
        var order=PortugueseOrderParser.parse(raw);
        // A new explicit order replaces only the pending proposal, never starts
        // terrain changes by accidentally confirming the previous proposal.
        if(order.action()==Action.CANCEL){
            pending=null;return new Reply(true,"Certo, vou parar a tarefa.",new Command(Action.CANCEL,0,location,null));
        }
        boolean choosingLayout=pending!=null && pending.stage()==Stage.LAYOUT
            && shelterChoice(text) && (order.action()==Action.BUILD_HOUSE || order.action()==Action.BUILD_SHELTER);
        boolean choosingFarm=pending!=null && pending.stage()==Stage.FARM_KIND
            && (order.action()==Action.CREATE_FARM || order.action()==Action.TEND_FARM);
        if(pending!=null && (order.action()==Action.NONE || choosingLayout || choosingFarm)){
            Command previous=pending.command();
            String wood=PortugueseOrderParser.woodSpecies(raw);
            if(pending.stage()==Stage.LAYOUT){
                if(shelterChoice(text)){
                    Command command=new Command(Action.BUILD_HOUSE,1,previous.origin(),wood==null?previous.wood():wood);
                    pending=new Pending(owner,dimension,now,now+CONFIRMATION_TICKS,Stage.CONFIRM,command);
                    return Reply.say(proposal(command));
                }
                if(affirmative(text))return Reply.say("Escolha o modelo primeiro: abrigo de madeira. Pode responder ‘abrigo’.");
                return Reply.ignored();
            }
            if(pending.stage()==Stage.FARM_KIND){
                Action selected=choosingFarm?order.action():farmChoice(text);
                if(selected!=Action.NONE){
                    Command command=new Command(selected,1,previous.origin(),null);
                    pending=new Pending(owner,dimension,now,now+CONFIRMATION_TICKS,Stage.CONFIRM,command);
                    return Reply.say(proposal(command));
                }
                if(affirmative(text))return Reply.say("Você quer criar uma plantação nova ou cuidar das que já existem? Responda ‘criar’ ou ‘cuidar’.");
                return Reply.ignored();
            }
            if(wood!=null && previous.action()==Action.BUILD_HOUSE && !wood.equals(previous.wood())){
                Command command=new Command(previous.action(),previous.amount(),previous.origin(),wood);
                pending=new Pending(owner,dimension,now,now+CONFIRMATION_TICKS,Stage.CONFIRM,command);
                return Reply.say(proposal(command));
            }
            if(affirmative(text)){
                pending=null;
                return new Reply(true,accepted(previous),previous);
            }
            return Reply.ignored();
        }
        if(order.action()==Action.NONE)return Reply.ignored();
        if(order.action()==Action.STATUS)return new Reply(true,"",new Command(Action.STATUS,0,location,null));
        if(order.action()==Action.FOLLOW){
            pending=null;return new Reply(true,"Certo, vou voltar a seguir você.",new Command(Action.FOLLOW,0,location,null));
        }
        if(order.action()==Action.WOOD || order.action()==Action.STONE || order.action()==Action.FOOD || order.action()==Action.HUNT_FOOD){
            pending=null;
            return new Reply(true,order.action()==Action.HUNT_FOOD?"Vou procurar alimento, dando prioridade aos animais.":"",
                new Command(order.action(),order.amount(),location,null));
        }
        if(order.action()==Action.BUILD_HOUSE || order.action()==Action.BUILD_SHELTER){
            String wood=PortugueseOrderParser.woodSpecies(raw);
            if(wood==null)wood=suggestedWood;
            Command command=new Command(Action.BUILD_HOUSE,1,location,wood);
            boolean specific=PortugueseOrderParser.mentionsShelter(raw);
            pending=new Pending(owner,dimension,now,now+CONFIRMATION_TICKS,specific?Stage.CONFIRM:Stage.LAYOUT,command);
            return Reply.say(specific?proposal(command):
                "Qual tipo de casa você quer? Tenho o abrigo de madeira, com porta, janelas, telhado, bancada e fornalha. Pode responder ‘abrigo’.");
        }
        if(order.action()==Action.FARM || order.action()==Action.CREATE_FARM || order.action()==Action.TEND_FARM){
            Command command=new Command(order.action(),1,location,null);
            pending=new Pending(owner,dimension,now,now+CONFIRMATION_TICKS,
                order.action()==Action.FARM?Stage.FARM_KIND:Stage.CONFIRM,command);
            return Reply.say(order.action()==Action.FARM?
                "Você quer criar uma plantação nova ou cuidar das que já existem? Responda ‘criar’ ou ‘cuidar’.":proposal(command));
        }
        return Reply.ignored();
    }
    private static boolean affirmative(String text){
        text=text.replaceAll("\\b(por favor|meu rei|amigo|mano|obrigado)\\b","").trim().replaceAll(" +"," ");
        return text.matches("(?:sim|simm|sin|ss|s|confirmo|confrimo|confirmar|pode|claro|certeza|isso|isso mesmo|ta bom|ta certo|esta certo|pode sim|sim pode|pode fazer|pode construir|sim pode construir|sim pode fazer|pode construir sim|pode fazer sim|pode comecar|pode comecar sim|pode iniciar|manda ver|beleza|bora)");
    }
    private static boolean negative(String text){
        return text.matches("(?:nao|naum|n|negativo|cancela|cancelar|cancele|pare|deixa pra la|desisto|nao quero|nao faca|nao pode|nao confirmo|agora nao)")
            || text.startsWith("nao ") || text.startsWith("naum ");
    }
    private static boolean shelterChoice(String text){
        return text.equals("1") || PortugueseOrderParser.mentionsShelter(text);
    }
    private static Action farmChoice(String text){
        if(text.equals("1") || PortugueseOrderParser.containsAny(text.split(" "),"criar","crie","nova","novo","construir","fazer"))return Action.CREATE_FARM;
        if(text.equals("2") || PortugueseOrderParser.containsAny(text.split(" "),"cuidar","cuide","existente","existentes","manter"))return Action.TEND_FARM;
        return Action.NONE;
    }
    private static String proposal(Command command){
        Location p=command.origin();String place=p.x()+", "+p.y()+", "+p.z();
        return switch(command.action()){
            case BUILD_HOUSE -> "Vou construir o abrigo perto de "+place+", usando só "+woodName(command.wood())
                +" na madeira. Vou preparar o terreno natural, limpar grama e neve, colocar porta, janelas, telhado, bancada e fornalha e buscar materiais que faltarem. Posso começar?";
            case CREATE_FARM -> "Vou fazer uma plantação perto de "+place
                +", preparar os canteiros, buscar materiais e levar água com um balde reutilizado. No final planto as sementes disponíveis e volto a seguir você. Posso preparar esse terreno?";
            case TEND_FARM -> "Posso cuidar das plantações existentes num raio de 100 blocos de "+place
                +"? Vou colher só plantas maduras, recolher os drops e replantar usando sementes reais, sem desmontar construções.";
            default -> "Posso começar essa tarefa?";
        };
    }
    private static String accepted(Command command){
        return switch(command.action()){
            case BUILD_HOUSE -> "Combinado. Vou preparar o local e construir o abrigo com "+woodName(command.wood())+".";
            case CREATE_FARM -> "Combinado. Vou preparar a plantação, buscar os materiais e levar a água.";
            case TEND_FARM -> "Combinado. Vou cuidar das plantações dessa área e replantar depois da colheita.";
            default -> "Combinado.";
        };
    }
    public static String woodName(String species){
        if(species==null)return "um único tipo de madeira escolhido no local";
        return switch(species){
            case "spruce"->"pinheiro";case "birch"->"bétula";case "jungle"->"madeira da selva";
            case "acacia"->"acácia";case "dark_oak"->"carvalho escuro";case "mangrove"->"mangue";
            case "cherry"->"cerejeira";default->"carvalho";
        };
    }
}
