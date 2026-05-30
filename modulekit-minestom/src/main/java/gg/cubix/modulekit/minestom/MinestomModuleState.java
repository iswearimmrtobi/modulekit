package gg.cubix.modulekit.minestom;

public enum MinestomModuleState {
    DISABLED,
    INITIALIZED,
    FAULTY;

    public static MinestomModuleState defaultState() {
        return DISABLED;
    }
}
