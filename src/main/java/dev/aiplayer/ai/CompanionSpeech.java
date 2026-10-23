package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import java.util.EnumMap;

/** Presentation only: never changes goals, navigation, inventory or the entity's random sequence. */
public final class CompanionSpeech {
    public enum Event {
        STONE("Bora buscar pedra exposta pra melhorar meus equipamentos, meu rei."),
        NO_STONE("Não achei pedra exposta com acesso seguro por aqui, viu? Vou tentar depois."),
        NO_TREE("Não achei árvore acessível nessa busca, meu rei. Pode ser distância ou caminho bloqueado."),
        NO_FOOD("Não achei animal adequado pra conseguir comida por aqui, viu?"),
        FULL("Ô meu rei, a mochila tá sem espaço pra essa coleta. Dá uma aliviada nela?", "Oxe, não cabe mais o que tô buscando. Preciso de espaço na mochila."),
        ROUTE("Esse caminho não tá dando passagem, viu? Vou tentar outra rota.", "Ô meu rei, não achei passagem segura por aqui."),
        RECOVERY("Calma aí, que tô procurando um jeito de sair daqui.", "Oxe, fiquei apertado aqui. Vou ver por onde dá pra sair."),
        BENCH("Não tô conseguindo trabalhar nessa bancada agora, viu? Vou tentar de novo depois.", "A bancada ficou complicada de alcançar. Vou guardar o lugar pra tentar depois."),
        RETREAT("Ô meu rei, o negócio apertou. Vou recuar um pouco!", "Assim tá pesado demais. Vou sair de perto pra me cuidar."),
        EAT("Vou comer um pouco pra recuperar as forças, viu?", "Pera aí, meu rei. Vou cuidar dessa vida aqui."),
        COMBAT("Opa, tem confusão por aqui. Fique ligado, meu rei!", "Oxe, apareceu problema. Tô de olho!"),
        CRAFT("Vou ajeitar meus materiais aqui, viu?", "Bora cuidar desses materiais e equipamentos, meu rei."),
        MATERIALS("Tô precisando de madeira pros equipamentos. Vou atrás dela.", "Faltou madeira pra preparar minhas coisas, viu? Vou buscar."),
        FOOD("Minha reserva de comida tá curta. Vou procurar alguma coisa.", "Bora providenciar comida, meu rei. Minha reserva tá pouca.");
        private final String[] lines;
        Event(String... lines){this.lines=lines;}
    }
    private final CompanionEntity npc;
    private final EnumMap<Event,Long> next=new EnumMap<>(Event.class);
    private final EnumMap<Event,Integer> variants=new EnumMap<>(Event.class);
    private long nextAny;
    private Event observed;
    private int observedAt;
    private boolean announced;
    private boolean enabled=true;
    public CompanionSpeech(CompanionEntity npc){this.npc=npc;}
    public boolean enabled(){return enabled;}
    public void enabled(boolean value){enabled=value;observed=null;announced=false;}
    /** Returns at most one automatic line every 8 seconds; same reason at most every minute. */
    public String line(Event event,long now){
        if(!enabled || event==null || now<nextAny || now<next.getOrDefault(event,Long.MIN_VALUE))return null;
        int index=variants.getOrDefault(event,0);
        String line=event.lines[Math.floorMod(index,event.lines.length)];
        variants.put(event,(index+1)%event.lines.length);next.put(event,now+1200);nextAny=now+160;
        return line;
    }
    public boolean notice(Event event){
        var owner=npc.owner();
        if(npc.getWorld().isClient || owner==null || npc.menuOpen() || npc.squaredDistanceTo(owner)>48*48)return false;
        String line=line(event,npc.getWorld().getTime());
        if(line==null)return false;
        owner.sendMessage(Text.literal("[AI Player] "+line),false);return true;
    }
    /** Debounce changing states and speak only once per sustained episode. No stale message queue. */
    public void observe(Event event){
        if(event!=observed){observed=event;observedAt=npc.age;announced=false;}
        if(event!=null && !announced && npc.age-observedAt>=40)announced=notice(event);
    }
    public void write(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putBoolean("Enabled",enabled);n.putLong("NextAny",nextAny);
        for(Event e:Event.values()){n.putLong("Next_"+e.name(),next.getOrDefault(e,Long.MIN_VALUE));n.putInt("Variant_"+e.name(),variants.getOrDefault(e,0));}
        root.put("Speech",n);
    }
    public void read(NbtCompound root){
        next.clear();variants.clear();observed=null;announced=false;nextAny=0;enabled=true;
        if(!root.contains("Speech"))return;
        NbtCompound n=root.getCompound("Speech");enabled=!n.contains("Enabled") || n.getBoolean("Enabled");nextAny=n.getLong("NextAny");
        for(Event e:Event.values()){if(n.contains("Next_"+e.name()))next.put(e,n.getLong("Next_"+e.name()));variants.put(e,n.getInt("Variant_"+e.name()));}
    }
}
