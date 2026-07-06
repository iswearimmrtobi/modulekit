# ModuleKit

A lightweight Java module framework that wires feature modules together via constructor injection and manages their lifecycle.

---

## What it does

ModuleKit lets you split an application into self-contained modules. Each module declares what services it **provides** and what services it **requires**. The framework builds a dependency graph, sorts modules into a safe load order, injects dependencies into constructors, and drives each module through its lifecycle.

```
discover() ──► build graph ──► topological sort ──► inject ──► onLoad ──► onEnable
```

Modules that fail to load are isolated — they do not prevent unrelated modules from starting.

---

## Project structure

| Subproject | Purpose |
|---|---|
| `modulekit-api` | Pure contract — `Module`, `ModuleDescriptor`, `LoadContext`. No external dependencies. |
| `modulekit-core` | Implementation — discovery, dependency graph, topological sort, injection. |
| `modulekit-paper` | Paper (Minecraft) adapter — bridges the lifecycle to `JavaPlugin`, adds listener bookkeeping and Brigadier command registration. |
| `modulekit-minestom` | Minestom (Minecraft) adapter — a standalone manager you wire into your own Minestom bootstrap. |

`modulekit-api` and `modulekit-core` have no platform dependencies and work in any plain Java application. The adapter subprojects show how to integrate ModuleKit with a specific runtime; you can write your own by extending `ModuleManager`.

---

## Writing a module

**1. Implement the module class**

```java
public final class ReportingModule extends Module<AppModuleState> {

    private final DatabaseService db;
    private final AuthService auth;

    // ModuleKit calls this constructor, injecting services from other modules
    public ReportingModule(DatabaseService db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("reporting", "Reporting")
            .requires(DatabaseService.class, AuthService.class)
            .provides(ReportingService.class)
            .build();
    }

    @Override
    public void onLoad(LoadContext ctx) {
        ctx.register(ReportingService.class, new ReportingServiceImpl(db, auth, ctx.dataFolder()));
    }

    @Override
    public void onEnable() {
        // start background tasks, register listeners, etc.
    }

    @Override
    public void onDisable() {
        // release resources, cancel tasks
    }
}
```

**2. Register the module for discovery**

Create `src/main/resources/META-INF/services/gg.cubix.modulekit.api.module.Module` containing the fully-qualified class name (blank lines and `#` comments are ignored):

```
com.example.app.reporting.ReportingModule
```

Discovery reads this file itself — it no longer goes through `java.util.ServiceLoader`, so module classes are **not required to have a public no-arg constructor**. To get a descriptor without instantiating the module (e.g. because your constructor requires injected services), declare a public **static** `getDescriptor()` instead of relying on the instance method:

```java
public static ModuleDescriptor getDescriptor() {
    return ModuleDescriptor.builder("reporting", "Reporting")
        .requires(DatabaseService.class, AuthService.class)
        .provides(ReportingService.class)
        .build();
}

@Override
public ModuleDescriptor descriptor() {
    return getDescriptor();
}
```

If no static `getDescriptor()` is found, discovery falls back to calling a no-arg constructor and the instance `descriptor()` method. If neither works, the module is discovered but marked faulty.

**3. Module Gradle setup**

```kotlin
plugins { `java-library` }

dependencies {
    api("gg.cubix:modulekit-api:1.1.0")
    compileOnly("gg.cubix:modulekit-core:1.1.0")
    compileOnly(project(":database"))  // for DatabaseService type reference
    compileOnly(project(":auth"))      // for AuthService type reference
}
```

---

## Bootstrapping (plain Java)

The base `ModuleManager` only provides discovery, storage, and the raw building blocks (`runAction` / `runActionReversed`) — it does not drive a lifecycle itself. Subclass it and drive the phases yourself using `InjectionResolver`, the same way `PaperModuleManager` and `MinestomModuleManager` do:

```java
public class AppModuleManager extends ModuleManager<AppModule, AppModuleState> {

    private final Map<Class<?>, Object> registry = new HashMap<>();

    public AppModuleManager() {
        super(AppModuleState.DISABLED, AppModuleState.FAULTY);
    }

    public LoadResult runLoad() {
        List<String> loaded = new ArrayList<>();
        List<LoadResult.FaultEntry> faulted = new ArrayList<>();

        runAction(ctx -> {
            var injection = InjectionResolver.resolve(ctx.descriptor(), ctx.moduleClass(), registry);
            if (injection.isFaulty()) {
                ctx.setState(AppModuleState.FAULTY);
                faulted.add(new LoadResult.FaultEntry(ctx.descriptor().id(), injection.faultReason()));
                return;
            }
            ctx.setModule((AppModule) injection.instance());
            // ctx.module().onLoad(...) — build your own LoadContext and call it here
            ctx.setState(AppModuleState.LOADED);
            loaded.add(ctx.descriptor().id());
        });

        return new LoadResult(List.copyOf(loaded), List.copyOf(faulted));
    }

    public void runEnable() {
        runAction(ctx -> { if (ctx.state() == AppModuleState.LOADED) { ctx.module().onEnable(); ctx.setState(AppModuleState.ENABLED); } });
    }

    public void runDisable() {
        runActionReversed(ctx -> { if (ctx.state() == AppModuleState.ENABLED) { ctx.module().onDisable(); ctx.setState(AppModuleState.DISABLED); } });
    }
}
```

For a concrete reference implementation (including a minimal `LoadContext`), see `PaperModuleManager` in `modulekit-paper`.

---

## Module lifecycle

| Phase | Method | What to do |
|---|---|---|
| Load | `onLoad(LoadContext ctx)` | Register services via `ctx.register()`, read config, open connections. |
| Enable | `onEnable()` | Start tasks, register listeners, activate functionality. |
| Disable | `onDisable()` | Cancel tasks, close connections, release resources. Called in **reverse** load order. |
| Reload | `onReload(LoadContext ctx)` | Default: `onDisable → onLoad → onEnable`. Override for config-only reloads. |

### `LoadContext`

| Method | Purpose |
|---|---|
| `ctx.register(Type.class, impl)` | Publish a service to other modules. Only valid during `onLoad`; a no-op with a warning afterward. |
| `ctx.markFaulty("reason")` | Abort this module's activation. Dependents will not load. |
| `ctx.dataFolder()` | `Path` to this module's data directory. **Not created automatically** — create it yourself if you write to it. |
| `ctx.logger()` | Logger scoped to this module's id. |

---

## Module states

`Module<S extends Enum<S>>` and `ModuleManager<M, S extends Enum<S>>` are generic over the state type — ModuleKit itself does not define a fixed set of states. You bring your own enum and pass its initial/faulty values into your `ModuleManager` constructor; the framework just stores the current `S` per module and updates it via `ctx.setState(...)`.

The two bundled adapters each define their own concrete enum for their own lifecycle shape:

```
PaperModuleState:    DISABLED ──► LOADED ──► ENABLED
                                     │
                                   FAULTY  (terminal — does not auto-recover)

MinestomModuleState: DISABLED ──► INITIALIZED
                          │
                        FAULTY  (terminal — does not auto-recover)
```

Whatever states you define, a module is marked `FAULTY` (or whichever value you pass as `faultyState`) if:
- Its required service type has no provider
- A circular dependency is detected
- It calls `ctx.markFaulty()` during `onLoad`
- Constructor injection fails (missing service, ambiguous constructor, or a throwing constructor)

A `FAULTY`-equivalent state is terminal and skipped in all lifecycle phases; it does not affect unrelated modules.

---

## Dependency injection

Services flow through the module graph automatically. The load order is topologically sorted so providers always run before consumers. When a module's turn comes, its required services are already in the registry.

```java
// Module A registers a service during onLoad
ctx.register(DatabaseService.class, new DatabaseServiceImpl(ctx.dataFolder()));

// Module B receives it at construction time — no service-locator calls needed
public ModuleB(DatabaseService db) { this.db = db; }
```

**Injection rules:**
- Declare required types in `ModuleDescriptor.requires(...)`.
- Provide exactly one constructor (public or non-public) whose parameter types match that set.
- Parameter order does not matter — matching is by type set.
- Two constructors with the same type-set are ambiguous and cause a fault.
- If `requires()` is empty, a no-arg constructor is used.

**System-priority modules:** call `.system()` on the descriptor builder to bias load order. When multiple modules are simultaneously ready to load (no unresolved dependencies between them), `system()`-flagged modules are picked first — useful for infrastructure modules other modules implicitly expect to be up early.

**External services:** override `externalServices()` on a `ModuleManager` subclass to declare types that are pre-registered outside of any module (e.g. Paper's `JavaPlugin`, or a database connection handed in at startup). This tells the dependency graph not to fault modules that `requires()` such a type — but the value must still be present in the injection registry for constructor injection to actually succeed. See `PaperModuleManager`, which pre-registers `JavaPlugin` this way and exposes `registerService(Class, Object)` for host apps to add more.

---

## `LoadResult`

`runLoad()` returns a `LoadResult` you can inspect at startup:

```java
LoadResult result = modules.runLoad();
if (!result.isClean()) {
    for (LoadResult.FaultEntry fault : result.faulted()) {
        logger.severe("Module '" + fault.moduleId() + "' failed: " + fault.reason());
    }
}
```

Both `runLoad()` and `runEnable()` also log a summary line automatically.

---

## Runtime lookups

```java
// By id
Optional<AppModule> mod = modules.getModule("reporting");

// By class
Optional<ReportingModule> mod = modules.getModule(ReportingModule.class);

// All contexts
for (ModuleContext<AppModule, AppModuleState> ctx : modules.contexts()) {
    System.out.println(ctx.descriptor().id() + " → " + ctx.state());
}
```

---

## Paper adapter

`PaperModule` bundles the boilerplate a Paper module needs on top of the raw `LoadContext`-based contract: a `JavaPlugin` reference, Bukkit listener bookkeeping, and command registration.

```java
public final class GamemodeModule extends PaperModule {

    public GamemodeModule(JavaPlugin plugin) {
        super(plugin);
    }

    public static ModuleDescriptor getDescriptor() {
        return ModuleDescriptor.builder("gamemode", "Gamemode").build();
    }

    @Override public ModuleDescriptor descriptor() { return getDescriptor(); }

    @Override protected void onLoad() {
        register(GamemodeService.class, new GamemodeServiceImpl());
    }

    @Override public void onEnable() {
        registerListener(new GamemodeListener());
    }

    @Override public void onDisable() {
        super.onDisable(); // unregisters listeners registered via registerListener()
    }

    @Override public List<CommandRegistration> commands() {
        return List.of(new CommandRegistration("gamemode", "Switch gamemode", List.of("gm"), new GamemodeCommand()));
    }
}
```

Notes:
- The constructor takes the owning `JavaPlugin`, stored as `protected final plugin`. Since this constructor isn't no-arg, use the static `getDescriptor()` pattern from above so discovery doesn't need to instantiate the class to read its descriptor.
- `onLoad(LoadContext)` / `onReload(LoadContext)` are `final` on `PaperModule` — override the no-arg `onLoad()` / `onReload()` hooks instead. `register(Class, T)` and `markFaulty(String)` are convenience methods that delegate to the stashed `LoadContext`, valid only during `onLoad()`.
- `registerListener(Listener)` registers against the plugin and is tracked automatically; `onDisable()`'s default implementation unregisters every tracked listener. If you override `onDisable()`, call `super.onDisable()` to keep that guarantee.
- Don't register listeners in `onLoad()` — do it in `onEnable()`.

Consuming plugin:

```java
public final class MyPlugin extends JavaPlugin {
    private PaperModuleManager modules;

    @Override
    public void onLoad() {
        modules = new PaperModuleManager(this);
        modules.discover(getClass().getClassLoader());
        LoadResult result = modules.runLoad();
        if (!result.isClean()) { /* log result.faulted() */ }
    }

    @Override
    public void onEnable() {
        modules.runEnable();
    }

    @Override
    public void onDisable() {
        modules.runDisable();
    }
}
```

`PaperModuleManager` also supports runtime hot enable/disable of individual modules (`loadAndEnableModule(id)`, `disableModule(id)`, `markFaulty(id, reason)`, `faultReason(id)`) — useful for admin commands that toggle a feature without restarting the plugin.

### Command registration

Modules declare their commands via `commands()`, returning `CommandRegistration(name, description, aliases, BasicCommand, bypassModuleGuard = false)`. Wire them up once, e.g. in `onEnable`:

```java
LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    modules.registerCommands(event.registrar(), (source, moduleId) ->
        source.getSender().sendMessage("That feature is currently disabled."));
});
```

Unless a `CommandRegistration` sets `bypassModuleGuard = true`, `registerCommands` wraps it in a `ModuleAwareCommand`: while the owning module isn't `ENABLED`, execution is blocked and `onBlocked` is invoked instead of the real command (and no tab-completions are suggested). This lets you register commands for a module up front without every command author having to check the module's state manually.

---

## Minestom adapter

`MinestomModule` declares a simpler two-phase lifecycle — `onInitialize(LoadContext ctx)` / `onTerminate()`, both mandatory (no default no-op). `MinestomModuleManager(Path dataDirectory, Logger logger)` is a standalone manager with `runInitialize()` / `runTerminate()` that you wire into your own Minestom bootstrap `main()` — it isn't tied to any extension-loading API.

---

## Consuming app Gradle setup

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    implementation("gg.cubix:modulekit-api:1.1.0")
    implementation("gg.cubix:modulekit-core:1.1.0")

    // Feature modules
    implementation(project(":database"))
    implementation(project(":auth"))
    implementation(project(":reporting"))
}

tasks.shadowJar {
    // Merges all module discovery registration files — required
    mergeServiceFiles {
        include("META-INF/services/**")
    }
}
```

`mergeServiceFiles` is mandatory when bundling multiple modules into a single jar. Without it, only one module's `META-INF/services` entry survives and the rest are invisible to discovery.

---

## What ModuleKit does not include

- Event bus
- Config file handling
- Database layer
- Messaging or i18n
- Permission checks

These belong in individual modules or the host application. ModuleKit's single responsibility is discovering modules, resolving their dependencies, injecting them, and managing their lifecycle. (Paper command registration is the one exception — see the Paper adapter section above.)