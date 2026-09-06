package gg.cubix.modulekit.folia;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoliaSchedulerTest {

    private JavaPlugin plugin;
    private Logger logger;
    private FoliaScheduler scheduler;

    private GlobalRegionScheduler globalScheduler;
    private RegionScheduler regionScheduler;
    private AsyncScheduler asyncScheduler;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        logger = Logger.getLogger("modulekit-folia-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        scheduler = new FoliaScheduler(plugin, logger);

        globalScheduler = mock(GlobalRegionScheduler.class);
        regionScheduler = mock(RegionScheduler.class);
        asyncScheduler = mock(AsyncScheduler.class);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(globalScheduler);
        bukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);
        bukkit.when(Bukkit::getAsyncScheduler).thenReturn(asyncScheduler);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private ScheduledTask liveTask() {
        ScheduledTask task = mock(ScheduledTask.class);
        when(task.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.IDLE);
        return task;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Consumer<ScheduledTask>> consumerCaptor() {
        return ArgumentCaptor.forClass(Consumer.class);
    }

    // --- Delegation -------------------------------------------------------

    @Test
    void global_runNow_delegatesToGlobalRegionScheduler() {
        ScheduledTask scheduled = liveTask();
        when(globalScheduler.run(eq(plugin), any())).thenReturn(scheduled);

        Task task = scheduler.global().runNow(() -> {});

        verify(globalScheduler).run(eq(plugin), any());
        assertSame(scheduled, task.handle());
        assertEquals(1, scheduler.trackedTaskCount());
    }

    @Test
    void global_run_isFireAndForget_andUsesExecute() {
        scheduler.global().run(() -> {});

        verify(globalScheduler).execute(eq(plugin), any(Runnable.class));
        assertEquals(0, scheduler.trackedTaskCount());
    }

    @Test
    void region_byLocation_delegatesToRegionScheduler() {
        Location location = mock(Location.class);
        ScheduledTask scheduled = liveTask();
        when(regionScheduler.runDelayed(eq(plugin), eq(location), any(), eq(40L))).thenReturn(scheduled);

        Task task = scheduler.region(location).runLater(() -> {}, 40L);

        verify(regionScheduler).runDelayed(eq(plugin), eq(location), any(), eq(40L));
        assertSame(scheduled, task.handle());
    }

    @Test
    void region_byChunk_delegatesToRegionScheduler() {
        World world = mock(World.class);
        ScheduledTask scheduled = liveTask();
        when(regionScheduler.runAtFixedRate(eq(plugin), eq(world), eq(3), eq(-7), any(), eq(0L), eq(20L)))
            .thenReturn(scheduled);

        Task task = scheduler.region(world, 3, -7).runRepeating(() -> {}, 0L, 20L);

        verify(regionScheduler).runAtFixedRate(eq(plugin), eq(world), eq(3), eq(-7), any(), eq(0L), eq(20L));
        assertSame(scheduled, task.handle());
    }

    @Test
    void entity_delegatesToTheEntitysOwnScheduler() {
        Entity entity = mock(Entity.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        ScheduledTask scheduled = liveTask();
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), isNull(), eq(5L), eq(10L))).thenReturn(scheduled);

        Task task = scheduler.entity(entity).runRepeating(() -> {}, 5L, 10L);

        verify(entityScheduler).runAtFixedRate(eq(plugin), any(), isNull(), eq(5L), eq(10L));
        assertSame(scheduled, task.handle());
    }

    @Test
    void entity_passesRetiredCallbackThrough() {
        Entity entity = mock(Entity.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        ScheduledTask scheduled = liveTask();
        when(entityScheduler.runDelayed(eq(plugin), any(), any(Runnable.class), eq(1L))).thenReturn(scheduled);

        List<String> calls = new java.util.ArrayList<>();
        scheduler.entity(entity).runLater(() -> {}, () -> calls.add("retired"), 1L);

        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        verify(entityScheduler).runDelayed(eq(plugin), any(), retired.capture(), eq(1L));
        retired.getValue().run();
        assertEquals(List.of("retired"), calls);
    }

    @Test
    void async_delegatesToAsyncScheduler_withTimeUnit() {
        ScheduledTask scheduled = liveTask();
        when(asyncScheduler.runDelayed(eq(plugin), any(), eq(250L), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(scheduled);

        Task task = scheduler.async().runLater(() -> {}, 250L, TimeUnit.MILLISECONDS);

        verify(asyncScheduler).runDelayed(eq(plugin), any(), eq(250L), eq(TimeUnit.MILLISECONDS));
        assertSame(scheduled, task.handle());
    }

    // --- Retired entities -------------------------------------------------

    @Test
    void entity_returnsNoneRatherThanNull_whenEntityIsRetired() {
        Entity entity = mock(Entity.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        // EntityScheduler returns null once the entity has been removed.
        when(entityScheduler.run(eq(plugin), any(), isNull())).thenReturn(null);

        Task task = scheduler.entity(entity).runNow(() -> {});

        assertSame(Task.none(), task);
        assertTrue(task.isCancelled());
        assertFalse(task.isRepeating());
        task.cancel();  // must not throw
        assertEquals(0, scheduler.trackedTaskCount(), "a task that was never scheduled is not tracked");
    }

    // --- Bookkeeping ------------------------------------------------------

    @Test
    void cancelAll_cancelsEveryTrackedTask_andClears() {
        ScheduledTask a = liveTask();
        ScheduledTask b = liveTask();
        when(globalScheduler.run(eq(plugin), any())).thenReturn(a);
        when(asyncScheduler.runNow(eq(plugin), any())).thenReturn(b);

        scheduler.global().runNow(() -> {});
        scheduler.async().runNow(() -> {});
        assertEquals(2, scheduler.trackedTaskCount());

        scheduler.cancelAll();

        verify(a).cancel();
        verify(b).cancel();
        assertEquals(0, scheduler.trackedTaskCount());
    }

    @Test
    void cancelAll_keepsGoing_whenOneTaskThrows() {
        ScheduledTask bad = liveTask();
        ScheduledTask good = liveTask();
        when(bad.cancel()).thenThrow(new IllegalStateException("boom"));
        when(globalScheduler.run(eq(plugin), any())).thenReturn(bad, good);

        scheduler.global().runNow(() -> {});
        scheduler.global().runNow(() -> {});

        scheduler.cancelAll();

        verify(bad).cancel();
        verify(good).cancel();
        assertEquals(0, scheduler.trackedTaskCount());
    }

    @Test
    void completedTasksArePruned_soTheSetDoesNotGrowUnbounded() {
        for (int i = 0; i < 40; i++) {
            ScheduledTask finished = mock(ScheduledTask.class);
            when(finished.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.FINISHED);
            when(globalScheduler.run(eq(plugin), any())).thenReturn(finished);
            scheduler.global().runNow(() -> {});
        }

        assertEquals(0, scheduler.trackedTaskCount());
        assertTrue(scheduler.isIdle());
    }

    @Test
    void cancelledTasksArePruned() {
        ScheduledTask cancelled = mock(ScheduledTask.class);
        when(cancelled.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.CANCELLED);
        when(globalScheduler.run(eq(plugin), any())).thenReturn(cancelled);

        scheduler.global().runNow(() -> {});

        assertEquals(0, scheduler.trackedTaskCount());
    }

    @Test
    void runningTasksAreNeverPruned() {
        for (int i = 0; i < 40; i++) {
            ScheduledTask running = mock(ScheduledTask.class);
            when(running.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.RUNNING);
            when(globalScheduler.run(eq(plugin), any())).thenReturn(running);
            scheduler.global().runNow(() -> {});
        }

        assertEquals(40, scheduler.trackedTaskCount());
    }

    // --- Exception containment --------------------------------------------

    @Test
    void aThrowingTaskIsLogged_notPropagatedIntoTheRegionTick() {
        List<LogRecord> records = new java.util.ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        ScheduledTask scheduled = liveTask();
        when(globalScheduler.run(eq(plugin), any())).thenReturn(scheduled);

        scheduler.global().runNow(() -> { throw new IllegalStateException("boom"); });

        ArgumentCaptor<Consumer<ScheduledTask>> body = consumerCaptor();
        verify(globalScheduler).run(eq(plugin), body.capture());

        body.getValue().accept(null);   // the platform invoking the task body

        assertEquals(1, records.size());
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertEquals("boom", records.get(0).getThrown().getMessage());
    }

    @Test
    void aThrowingFireAndForgetTaskIsAlsoContained() {
        scheduler.global().run(() -> { throw new IllegalStateException("boom"); });

        ArgumentCaptor<Runnable> body = ArgumentCaptor.forClass(Runnable.class);
        verify(globalScheduler).execute(eq(plugin), body.capture());

        body.getValue().run();   // must not throw

        verify(globalScheduler, never()).run(any(), any());
    }
}
