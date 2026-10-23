import dev.aiplayer.ai.brain.DecisionPolicy;
import static dev.aiplayer.ai.brain.DecisionPolicy.Activity.*;
import java.util.*;
public class DecisionPolicyTest {
    private static int checks;
    private static DecisionPolicy.Candidate c(DecisionPolicy.Activity a,int p){return new DecisionPolicy.Candidate(a,p,a.name());}
    private static void expect(DecisionPolicy.Activity want,DecisionPolicy.Activity current,DecisionPolicy.Candidate... choices){
        var got=DecisionPolicy.choose(Arrays.asList(choices),current);
        if(got.activity()!=want)throw new AssertionError("Expected "+want+" got "+got);
        checks++;
    }
    public static void main(String[] args) {
        expect(IDLE,GATHER);
        expect(COMBAT,GATHER,c(GATHER,50),c(COMBAT,100),c(CRAFT,65));
        expect(RETREAT,COMBAT,c(GATHER,50),c(RETREAT,100));
        expect(EAT,RETREAT,c(RETREAT,100),c(EAT,115));
        expect(WAIT,COMBAT,c(WAIT,200),c(COMBAT,100));
        expect(CLEANUP,FOLLOW,c(FOLLOW,70),c(CLEANUP,75));
        expect(INVENTORY_FULL,GATHER,c(GATHER,60),c(INVENTORY_FULL,62));
        expect(GATHER,COMBAT,c(GATHER,60),c(IDLE,0));
        expect(GATHER,GATHER,c(CRAFT,50),c(GATHER,50));
        expect(FOLLOW,GATHER,c(FOLLOW,70),c(GATHER,50));
        for(int i=0;i<100;i++){
            var choices=new ArrayList<>(List.of(c(CRAFT,65),c(RETREAT,100),c(GATHER,60),c(IDLE,0)));
            Collections.shuffle(choices,new Random(i));
            if(DecisionPolicy.choose(choices,CRAFT).activity()!=RETREAT)throw new AssertionError("Order-dependent safety priority");
            checks++;
        }
        System.out.println(checks+" arbitration checks passed");
    }
}
