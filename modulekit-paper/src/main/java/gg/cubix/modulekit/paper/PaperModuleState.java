package gg.cubix.modulekit.paper;

public enum PaperModuleState {
    DISABLED,
    LOADED,
    ENABLED,
    FAULTY;

    public static PaperModuleState defaultState() {
        return DISABLED;
    }
}
