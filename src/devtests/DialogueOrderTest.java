import dev.aiplayer.commands.OrderConversation;
import dev.aiplayer.commands.PortugueseOrderParser;
import java.util.UUID;
import static dev.aiplayer.commands.PortugueseOrderParser.Action.*;

public final class DialogueOrderTest {
    private static int checks;
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OrderConversation.Location ORIGINAL=new OrderConversation.Location(10,64,20);
    private static final OrderConversation.Location MOVED=new OrderConversation.Location(60,70,80);
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void parse(String text,PortugueseOrderParser.Action expected){
        var order=PortugueseOrderParser.parse(text);check(order.action()==expected,text+" => "+order);
    }
    private static OrderConversation.Reply say(OrderConversation state,String raw,long now){
        return state.accept(raw,OWNER,"minecraft:overworld",now,ORIGINAL,null);
    }
    public static void main(String[] args){
        parse("vc pode contrua um abrigo pra mim",BUILD_HOUSE);
        parse("fassa uma caza de pinhero",BUILD_HOUSE);
        parse("faça uma plantassao",CREATE_FARM);
        parse("monte uma horta",CREATE_FARM);
        parse("cuida das prantacoes",TEND_FARM);
        parse("colha trigo e replante",TEND_FARM);
        parse("mantenha a lavoura",TEND_FARM);
        parse("toma conta da minha horta",TEND_FARM);
        parse("vigie as plantações",TEND_FARM);
        parse("plante trigo",FARM);
        parse("semeie trigo",FARM);
        parse("ache comida",HUNT_FOOD);
        parse("cace animais",HUNT_FOOD);
        parse("procure alimentos",HUNT_FOOD);
        parse("não cuide da plantação",NONE);
        parse("construa abrigo e pegue madeira",NONE);
        parse("construa uma casa e uma horta",NONE);
        parse("explore a área",NONE);
        parse("o que vc ta fazendo",STATUS);
        parse("como vai a plantação",STATUS);
        parse("qual tarefa",STATUS);
        parse("volte a me seguir",FOLLOW);
        parse("pare",CANCEL);
        parse("pare de construir",CANCEL);
        parse("cancele por favor",CANCEL);
        parse("eu achei comida",NONE);
        parse("ela cuida da horta",NONE);
        check("spruce".equals(PortugueseOrderParser.woodSpecies("de pinhero")),"Wood typo was not recognized");
        check("dark_oak".equals(PortugueseOrderParser.woodSpecies("carvalho escuro")),"Dark oak became ordinary oak");
        check("dark_oak".equals(PortugueseOrderParser.woodSpecies("carvaio escuro")),"Dark oak typo lost dark palette");

        OrderConversation house=new OrderConversation();
        var prompt=say(house,"construa uma casa",10);
        check(prompt.handled() && prompt.execute()==null && prompt.text().contains("Qual tipo"),"Generic house did not ask layout");
        check(say(house,"sim",11).execute()==null,"Yes selected an unspecified house layout");
        var choose=house.accept("quero o abrigo de pinheiro",OWNER,"minecraft:overworld",12,MOVED,null);
        check(choose.execute()==null && choose.text().contains("10, 64, 20") && choose.text().contains("pinheiro"),"Layout choice moved the original site");
        var confirmed=house.accept("sim pode fazer meu rei",OWNER,"minecraft:overworld",13,MOVED,null);
        check(confirmed.execute()!=null && confirmed.execute().action()==BUILD_HOUSE,"Confirmed shelter did not start");
        check(confirmed.execute().origin().equals(ORIGINAL) && "spruce".equals(confirmed.execute().wood()),"Confirmation lost original origin or palette");
        check(!say(house,"sim",14).handled(),"Confirmation executed twice");

        OrderConversation cancel=new OrderConversation();
        say(cancel,"faça uma horta",20);
        var declined=say(cancel,"não",21);
        check(declined.handled() && declined.execute()==null && !cancel.hasPending(OWNER,"minecraft:overworld",22),"Declining a proposal cancelled/started actual work");
        check(!say(cancel,"sim",22).handled(),"Declined proposal remained executable");

        OrderConversation scoped=new OrderConversation();say(scoped,"construa abrigo",30);
        check(!scoped.accept("sim",OTHER,"minecraft:overworld",31,MOVED,null).handled(),"Other player confirmed owner's request");
        check(say(scoped,"sim",32).execute()!=null,"Wrong owner destroyed legitimate owner's pending request");
        OrderConversation transferred=new OrderConversation();say(transferred,"construa abrigo",33);
        transferred.currentOwner(OTHER);
        check(!transferred.accept("sim",OTHER,"minecraft:overworld",34,MOVED,null).handled(),"New owner inherited another owner's proposal");
        check(transferred.accept("faça uma horta",OTHER,"minecraft:overworld",35,MOVED,null).handled(),"Owner transfer left dialogue permanently blocked");
        OrderConversation dimension=new OrderConversation();say(dimension,"faça uma horta",40);
        check(dimension.accept("sim",OWNER,"minecraft:the_nether",41,MOVED,null).execute()==null,"Cross-dimension confirmation started project");
        check(!dimension.hasPending(OWNER,"minecraft:overworld",42),"Dimension change retained old proposal");
        OrderConversation timeout=new OrderConversation();say(timeout,"construa abrigo",50);
        check(say(timeout,"sim",50+OrderConversation.CONFIRMATION_TICKS+1).execute()==null,"Expired confirmation started project");
        OrderConversation reversed=new OrderConversation();say(reversed,"construa abrigo",500);
        check(say(reversed,"sim",100).execute()==null,"Rewound world clock kept a stale confirmation");

        OrderConversation firstNpc=new OrderConversation(),secondNpc=new OrderConversation();say(firstNpc,"construa abrigo",60);
        check(!say(secondNpc,"sim",61).handled(),"Different NPC inherited pending confirmation");
        check(say(firstNpc,"sim",62).execute()!=null,"First NPC lost its own confirmation");
        OrderConversation replace=new OrderConversation();say(replace,"faça uma plantação",70);
        check(say(replace,"pegue 10 madeira",71).execute().action()==WOOD,"Explicit wood order did not replace pending proposal");
        check(!say(replace,"sim",72).handled(),"Replaced proposal remained executable");
        OrderConversation mixed=new OrderConversation();say(mixed,"construa abrigo",73);
        check(say(mixed,"sim faça uma plantação",74).execute()==null,"Mixed new task accidentally confirmed old house");
        check(say(mixed,"sim",75).execute().action()==CREATE_FARM,"Mixed new farm request did not get its own confirmation");
        OrderConversation hesitant=new OrderConversation();say(hesitant,"construa abrigo",76);
        check(say(hesitant,"sim mas não aqui",77).execute()==null,"Negative qualification was accepted as certain confirmation");

        OrderConversation wood=new OrderConversation();say(wood,"construa um abrigo de pinheiro",80);
        var altered=wood.accept("use carvalho escuro",OWNER,"minecraft:overworld",81,MOVED,null);
        check(altered.execute()==null && altered.text().contains("carvalho escuro"),"Palette change executed without reconfirming");
        var changed=say(wood,"confirmo",82).execute();
        check(changed!=null && "dark_oak".equals(changed.wood()) && changed.origin().equals(ORIGINAL),"Palette change lost site or selection");

        OrderConversation planting=new OrderConversation();
        check(say(planting,"plante trigo",90).text().contains("criar"),"Bare planting did not ask new/existing farm");
        check(say(planting,"sim",91).execute()==null,"Yes selected an unspecified farm action");
        var selected=planting.accept("quero cuidar das plantações",OWNER,"minecraft:overworld",92,MOVED,null);
        check(selected.execute()==null && selected.text().contains("100 blocos") && selected.text().contains("10, 64, 20"),"Care choice changed origin or omitted care permission");
        check(say(planting,"pode",93).execute().action()==TEND_FARM,"Care confirmation started another task");
        OrderConversation create=new OrderConversation();say(create,"faça uma plantação",100);
        check(say(create,"sim",101).execute().action()==CREATE_FARM,"Farm construction intent became crop care");
        OrderConversation status=new OrderConversation();say(status,"construa abrigo",110);
        check(say(status,"status",111).execute().action()==STATUS && status.hasPending(OWNER,"minecraft:overworld",112),"Status query destroyed pending confirmation");
        check(say(status,"sim",113).execute().action()==BUILD_HOUSE,"Status query changed the pending project");
        System.out.println(checks+" dialogue/order checks passed");
    }
}
