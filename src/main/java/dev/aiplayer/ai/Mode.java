package dev.aiplayer.ai;
public enum Mode {
    FOLLOW("Seguir"), GUARD("Proteger"), WAIT("Esperar");
    public final String label;
    Mode(String label) { this.label = label; }
    public static Mode fromId(int id) { return id >= 0 && id < values().length ? values()[id] : WAIT; }
}
