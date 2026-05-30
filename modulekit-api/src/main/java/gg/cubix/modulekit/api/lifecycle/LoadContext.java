package gg.cubix.modulekit.api.lifecycle;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface LoadContext {

    Logger logger();

    Path dataFolder();

    <T> void register(Class<T> type, T instance);

    void markFaulty(String reason);
}
