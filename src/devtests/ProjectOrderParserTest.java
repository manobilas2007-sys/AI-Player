import dev.aiplayer.commands.PortugueseOrderParser;
import static dev.aiplayer.commands.PortugueseOrderParser.Action.*;
public class ProjectOrderParserTest {
    private static int checks;
    private static void check(String text,PortugueseOrderParser.Action expected){
        var result=PortugueseOrderParser.parse(text);
        if(result.action()!=expected)throw new AssertionError(text+" => "+result);
        checks++;
    }
    public static void main(String[] args){
        check("construa um abrigo de madeira",BUILD_HOUSE);
        check("construir uma casa",BUILD_HOUSE);
        check("plante trigo",FARM);
        check("cultive uma plantação de trigo",FARM);
        check("explore a área",NONE);
        check("explorar",NONE);
        check("cancele o projeto",CANCEL);
        check("cancelar a exploração",CANCEL);
        check("não construa abrigo",NONE);
        check("não plante trigo",NONE);
        check("não explore",NONE);
        check("estou explorando a floresta",NONE);
        check("construa abrigo e plante trigo",NONE);
        check("explique o mapa",NONE);
        check("explore e colete madeira",NONE);
        System.out.println(checks+" project command checks passed");
    }
}
