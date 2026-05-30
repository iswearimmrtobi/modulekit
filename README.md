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
| `modulekit-paper` | Paper (Minecraft) adapter — bridges the lifecycle to `JavaPlugin`. |
| `modulekit-minestom` | Minestom (Minecraft) adapter — bridges to `Extension`. |

`modulekit-api` and `modulekit-core` have no platform dependencies and work in any Java 21 application. The adapter subprojects show how to integrate ModuleKit with a specific runtime; you can write your own by extending `ModuleManager`.

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

**2. Register the module with `ServiceLoader`**

Create `src/main/resources/META-INF/services/gg.cubix.modulekit.api.module.Module` containing the fully-qualified class name:

```
com.example.app.reporting.ReportingModule
```

**3. Module Gradle setup**

```kotlin
plugins { `java-library` }

dependencies {
    api("gg.cubix:modulekit-api:1.0.0")
    compileOnly("gg.cubix:modulekit-core:1.0.0")
    compileOnly(project(":database"))  // for DatabaseService type reference
    compileOnly(project(":auth"))      // for AuthService type reference
}
```

---

## Bootstrapping (plain Java)

Without a platform adapter, drive the lifecycle directly through `ModuleManager`:

```java
ModuleManager<AppModule, AppModuleState> modules =
    new ModuleManager<>(AppModuleState.DISABLED, AppModuleState.FAULTY);

modules.discover(MyApp.class.getClassLoader());
modules.runLoad();
modules.runEnable();

// ... application runs ...

modules.runDisable();
```

For a concrete platform integration, see `PaperModuleManager` as a reference — it extends `ModuleManager` and adds Paper-specific lifecycle wiring in about 60 lines.

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
| `ctx.register(Type.class, impl)` | Publish a service to other modules. Only valid during `onLoad`. |
| `ctx.markFaulty("reason")` | Abort this module's activation. Dependents will not load. |
| `ctx.dataFolder()` | `Path` to this module's data directory (created automatically). |
| `ctx.logger()` | Logger scoped to this module's id. |

---

## Module states

```
DISABLED ──► LOADED ──► ENABLED
                │
              FAULTY  (terminal — does not auto-recover)
```

A module is marked `FAULTY` if:
- Its required service type has no provider
- A circular dependency is detected
- It calls `ctx.markFaulty()` during `onLoad`
- Constructor injection fails

Faulty modules are skipped in all lifecycle phases and do not affect unrelated modules.

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
- Provide exactly one constructor whose parameter types match that set.
- Parameter order does not matter — matching is by type.
- Two constructors with the same type-set in different order are ambiguous and cause a fault.

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

## Consuming app Gradle setup

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    implementation("gg.cubix:modulekit-api:1.0.0")
    implementation("gg.cubix:modulekit-core:1.0.0")

    // Feature modules
    implementation(project(":database"))
    implementation(project(":auth"))
    implementation(project(":reporting"))
}

tasks.shadowJar {
    // Merges all module ServiceLoader registration files — required
    mergeServiceFiles {
        include("META-INF/services/**")
    }
}
```

`mergeServiceFiles` is mandatory when bundling multiple modules into a single jar. Without it, only one module's `META-INF/services` entry survives and the rest are invisible to `ServiceLoader`.

---

## What ModuleKit does not include

- Command registration
- Event bus
- Config file handling
- Database layer
- Messaging or i18n
- Permission checks

These belong in individual modules or the host application. ModuleKit's single responsibility is discovering modules, resolving their dependencies, injecting them, and managing their lifecycle.
