package main.core;

public final class Agent {
    private final String id;
    private final String personality;
    private final String priorities;

    public Agent(String id, String personality, String priorities) {
        this.id          = id;
        this.personality = personality;
        this.priorities  = priorities;
    }

    public String getId()          { return id; }
    public String getPersonality() { return personality; }
    public String getPriorities()  { return priorities; }
}