import dev.aiplayer.commands.PortugueseOrderParser;
import static dev.aiplayer.commands.PortugueseOrderParser.Action.*;
public class PortugueseOrderParserTest {
    private static void check(String text, PortugueseOrderParser.Action action, int qty) {
        var result=PortugueseOrderParser.parse(text);
        if(result.action()!=action || result.amount()!=qty)throw new AssertionError(text+" => "+result);
    }
    public static void main(String[] args) {
        check("você pode pega 10 tronco de madeira pra mim?", WOOD, 10);
        check("vc consegue pege 16 troncus de madeirra",WOOD,16);
        check("coleta 64 toras",WOOD,64);
        check("traga sessenta e quatro madeira",WOOD,64);
        check("busca duas toras",WOOD,2);
        check("traga duzentos e sessenta e quatro troncos",WOOD,264);
        check("arranje 11 tronco de madeira",WOOD,11);
        check("pegue 6 carnes",FOOD,6);
        check("coletar 10 alimentos",FOOD,10);
        check("cancelar tarefa",CANCEL,0);
        check("nao pega madeira",NONE,0);
        check("oi estou na floresta",NONE,0);
        check("pegue 900 madeira",NONE,0);
        check("pegue 16 pedregulhos",STONE,16);
        check("minere dezesseis pedras",STONE,16);
        check("pege 12 pedregulo",STONE,12);
        check("coelte 24 pedar",STONE,24);
        check("traga um pack de cobblestone",STONE,64);
        check("quero duas stacks de pedra",STONE,128);
        check("busque vinte e quatro rochas",STONE,24);
        check("minera pedra",STONE,16);
        check("cancele a mineração de pedra",CANCEL,0);
        check("não traga pedra",NONE,0);
        check("não quero que pegue madeira",NONE,0);
        check("naum coelte pedar",NONE,0);
        check("não cancele a coleta",NONE,0);
        check("pega pedra e madeira",NONE,0);
        check("quero uma picareta de pedra",NONE,0);
        check("tem pedra ali",NONE,0);
        check("pegue -2 pedras",NONE,0);
        check("pegue 1.5 pedras",NONE,0);
        check("pegue 16 ou 32 pedras",NONE,0);
        check("pegue 0 pedras",NONE,0);
        check("pegue 999999999999999 pedras",NONE,0);
        check("pegue cinco packs de pedra",NONE,0);
        check("pegue dezessete pedras",STONE,17);
        check("traga cento e vinte e oito pedras",STONE,128);
        check("colete 4 lenha",WOOD,4);
        System.out.println("38 testes do interpretador: OK");
    }
}
