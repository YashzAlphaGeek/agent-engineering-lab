package golden.agent.guard;

/** The three outcomes of a gate: allow, deny, or ask a human. */
public sealed interface Decision {

    /** The call may run. */
    record Allow() implements Decision { }

    /** The call must not run. The reason is returned to the model as an error result. */
    record Deny(String reason) implements Decision { }

    /** A person must approve first. No answer means deny. */
    record AskHuman(String reason) implements Decision { }

    static Decision allow() {
        return new Allow();
    }

    static Decision deny(String reason) {
        return new Deny(reason);
    }

    static Decision askHuman(String reason) {
        return new AskHuman(reason);
    }
}
