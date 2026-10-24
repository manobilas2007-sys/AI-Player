package dev.aiplayer.ai.brain;

import java.util.Comparator;
import java.util.List;

/** Pure arbitration policy. Executors retain their own resumable progress; only one wins a tick. */
public final class DecisionPolicy {
    public enum Activity { IDLE, WAIT, FOLLOW, GATHER, CRAFT, CLEANUP, EAT, COMBAT, RETREAT, ESCAPE, INVENTORY_FULL, PROJECT }
    public record Candidate(Activity activity, int priority, String reason) {}
    public record Decision(Activity activity, String reason) {}
    private DecisionPolicy() {}
    public static Decision choose(List<Candidate> candidates, Activity current) {
        return candidates.stream().max(Comparator
                .comparingInt((Candidate c) -> c.priority())
                .thenComparingInt(c -> c.activity()==current?1:0))
            .map(c -> new Decision(c.activity(),c.reason()))
            .orElse(new Decision(Activity.IDLE,"Sem necessidade urgente"));
    }
}
