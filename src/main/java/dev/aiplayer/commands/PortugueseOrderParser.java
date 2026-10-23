package dev.aiplayer.commands;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small offline command parser, not a generative language model. */
public final class PortugueseOrderParser {
    public enum Action { WOOD, FOOD, STONE, CANCEL, NONE }
    public record Order(Action action, int amount) {}
    private static final Pattern DIGITS = Pattern.compile("(?<![0-9])([0-9]{1,4})(?![0-9])");
    private PortugueseOrderParser() {}

    public static String normalize(String value) {
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");
    }
    private static int edits(String a,String b){
        int[][] d=new int[a.length()+1][b.length()+1];
        for(int i=0;i<=a.length();i++)d[i][0]=i;
        for(int j=0;j<=b.length();j++)d[0][j]=j;
        for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++){
            d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            if(i>1 && j>1 && a.charAt(i-1)==b.charAt(j-2) && a.charAt(i-2)==b.charAt(j-1))d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);
        }
        return d[a.length()][b.length()];
    }
    private static boolean close(String token, String... options) {
        for(String option: options) {
            if(token.equals(option))return true;
            if(token.length()>=4 && option.length()>=4 && edits(token,option)<= (Math.min(token.length(),option.length())>=7?2:1))return true;
        }
        return false;
    }
    private static boolean containsAny(String[] words,String... options) {
        for(String word:words)if(close(word,options))return true;
        return false;
    }
    public static Order parse(String raw) {
        if(raw==null || raw.isBlank() || raw.length()>512)return new Order(Action.NONE,0);
        String text=normalize(raw);
        String[] words=text.split(" ");
        if(text.matches(".*\\b(nao|nunca|nem|naum|n)\\b.*"))return new Order(Action.NONE,0);
        boolean cancel=containsAny(words,"cancelar","cancela","pare","parar","interromper","desista");
        if(cancel && containsAny(words,"tarefa","coleta","madeira","troncos","trabalho","ordem","buscar","pegar","pedra","pedregulho","mineracao"))
            return new Order(Action.CANCEL,0);
        boolean wood=containsAny(words,"madeira","madeiras","tronco","troncos","tora","toras","arvore","arvores","lenha");
        boolean food=containsAny(words,"comida","alimento","alimentos","carne","carnes");
        boolean stone=containsAny(words,"pedra","pedras","pedregulho","pedregulhos","rocha","rochas","cobble","cobblestone");
        boolean request=containsAny(words,"pega","pegar","pegue","pege","apanha","apanhar","busca","buscar","busque","procura","procurar","coleta","coletar","colhe","colher","corta","cortar","traz","trazer","traga","arruma","arrumar","arranje","junta","juntar","consegue","consiga","obter","preciso","quero","minera","minerar","mine","extrai","extrair","quebra","quebre","quebrar","colete");
        if(!request || (wood?1:0)+(food?1:0)+(stone?1:0)!=1)return new Order(Action.NONE,0);
        if(text.matches(".*\\b(picareta|picaretas|machado|machados|espada|espadas|ferramenta|ferramentas)\\b.*"))return new Order(Action.NONE,0);
        if(Pattern.compile("-\\s*\\d|\\d[.,]\\d").matcher(raw).find())return new Order(Action.NONE,0);
        int amount=stone?16:wood?8:4;
        boolean explicit=false;
        Matcher found=Pattern.compile("[0-9]+").matcher(text);
        if(found.find()){
            if(found.group().length()>3)return new Order(Action.NONE,0);
            amount=Integer.parseInt(found.group());explicit=true;
            if(found.find())return new Order(Action.NONE,0);
        }else{
            String[] names={"um","uma","dois","duas","tres","quatro","cinco","seis","sete","oito","nove","dez","onze","doze","treze","quatorze","catorze","quinze","dezesseis","dezasseis","dezessete","dezoito","dezenove","vinte","trinta","quarenta","cinquenta","sessenta","setenta","oitenta","noventa","cem","cento","duzentos"};
            int[] nums={1,1,2,2,3,4,5,6,7,8,9,10,11,12,13,14,14,15,16,16,17,18,19,20,30,40,50,60,70,80,90,100,100,200};
            java.util.Map<String,Integer> numbers=new java.util.HashMap<>();
            for(int i=0;i<names.length;i++)numbers.put(names[i],nums[i]);
            for(int i=0;i<words.length;i++){
                Integer n=numbers.get(words[i]);if(n==null)continue;
                if(explicit)return new Order(Action.NONE,0);
                explicit=true;amount=n;
                while(i+2<words.length && words[i+1].equals("e") && numbers.containsKey(words[i+2])){
                    int part=numbers.get(words[i+2]);if(part>=n || n<20)return new Order(Action.NONE,0);
                    amount+=part;n=part;i+=2;
                }
            }
        }
        if(text.matches(".*\\b(pack|packs|stack|stacks|pilha|pilhas)\\b.*"))amount=(explicit?amount:1)*64;
        if(amount<1 || amount>264)return new Order(Action.NONE,0);
        return new Order(stone?Action.STONE:wood?Action.WOOD:Action.FOOD,amount);
    }
}
