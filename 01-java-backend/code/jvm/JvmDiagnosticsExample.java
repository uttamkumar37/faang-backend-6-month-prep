package com.faangprep.javabackend.jvm;

import java.lang.ref.*;
import java.lang.management.*;
import javax.management.NotificationEmitter;
import java.util.*;

/**
 * JVM Diagnostics & Internals — runnable examples
 *
 * Topics covered:
 *   1. Reading Runtime memory stats
 *   2. GC monitoring via MXBeans
 *   3. Weak / Soft / Phantom reference behavior
 *   4. Thread dumps and stack traces programmatically
 *   5. Class loading inspection
 *   6. JVM flag inspection
 */
public class JvmDiagnosticsExample {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. MEMORY STATS
    // ─────────────────────────────────────────────────────────────────────────

    static void printMemoryStats() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        System.out.printf("%-15s  used=%,d MB  committed=%,d MB  max=%,d MB%n",
                "Heap",
                heap.getUsed() / (1024 * 1024),
                heap.getCommitted() / (1024 * 1024),
                heap.getMax() / (1024 * 1024));

        System.out.printf("%-15s  used=%,d MB  committed=%,d MB%n",
                "Non-Heap",
                nonHeap.getUsed() / (1024 * 1024),
                nonHeap.getCommitted() / (1024 * 1024));

        // Per-pool breakdown (Eden, Survivor, Old Gen, Metaspace, Code Cache)
        ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> {
            MemoryUsage usage = pool.getUsage();
            System.out.printf("  pool=%-30s  used=%,d KB%n",
                    pool.getName(), usage.getUsed() / 1024);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GC MONITORING
    // ─────────────────────────────────────────────────────────────────────────

    static void printGcStats() {
        System.out.println("\n─ GC Stats ───────────────────────────────────");
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            System.out.printf("  %-30s  collections=%d  totalTime=%dms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        });
    }

    // Register a notification listener to track GC pauses in real-time
    static void registerGcListener() {
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gcBean -> {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    System.out.printf("[GC] type=%s time=%dms%n",
                            notification.getType(),
                            notification.getTimeStamp());
                }, null, null);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. REFERENCE TYPES
    // ─────────────────────────────────────────────────────────────────────────

    static void demonstrateReferences() throws InterruptedException {
        // Strong reference — survives GC as long as ref is reachable
        String strong = new String("I will not be collected");

        // Soft reference — JVM keeps as long as memory allows
        SoftReference<byte[]> softRef = new SoftReference<>(new byte[1024]);
        // Forcing GC won't clear a soft ref unless memory is very low
        System.gc();
        System.out.println("Soft ref after GC: " + (softRef.get() != null ? "alive" : "cleared"));

        // Weak reference — cleared on next GC regardless
        WeakReference<String> weakRef = new WeakReference<>(new String("weak"));
        System.gc();
        Thread.sleep(100);
        System.out.println("Weak ref after GC: " + (weakRef.get() != null ? "alive" : "cleared"));

        // Phantom reference with ReferenceQueue — called after finalization
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object phantom = new Object();
        PhantomReference<Object> phantomRef = new PhantomReference<>(phantom, queue);
        phantom = null; // release strong reference
        System.gc();
        Thread.sleep(100);
        // phantomRef.get() always returns null — PhantomReference prevents access to referent
        System.out.println("Phantom ref cleared: " + phantomRef.refersTo(null));
        Reference<?> enqueued = queue.poll();
        System.out.println("Phantom reference enqueued: " + (enqueued != null));

        // Prevent compiler from eliding 'strong'
        System.out.println("Strong: " + strong.length());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. THREAD INSPECTION
    // ─────────────────────────────────────────────────────────────────────────

    static void inspectThreads() {
        System.out.println("\n─ Thread States ──────────────────────────────");
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        for (long id : threadBean.getAllThreadIds()) {
            ThreadInfo info = threadBean.getThreadInfo(id);
            if (info != null) {
                System.out.printf("  %-40s  state=%-15s%n",
                        info.getThreadName(), info.getThreadState());
            }
        }

        // Deadlock detection
        long[] deadlocked = threadBean.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("DEADLOCK DETECTED on thread IDs: " + Arrays.toString(deadlocked));
        } else {
            System.out.println("\nNo deadlocks detected.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CLASS LOADING STATS
    // ─────────────────────────────────────────────────────────────────────────

    static void inspectClassLoading() {
        ClassLoadingMXBean clBean = ManagementFactory.getClassLoadingMXBean();
        System.out.printf("%nClass Loading: loaded=%d  totalLoaded=%d  unloaded=%d%n",
                clBean.getLoadedClassCount(),
                clBean.getTotalLoadedClassCount(),
                clBean.getUnloadedClassCount());

        // Show the classloader hierarchy for a known class
        ClassLoader cl = JvmDiagnosticsExample.class.getClassLoader();
        System.out.print("ClassLoader chain: ");
        while (cl != null) {
            System.out.print(cl.getClass().getSimpleName() + " → ");
            cl = cl.getParent();
        }
        System.out.println("Bootstrap");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RUNTIME FLAGS
    // ─────────────────────────────────────────────────────────────────────────

    static void printRuntimeInfo() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        System.out.println("\n─ JVM Runtime Info ───────────────────────────");
        System.out.println("VM Name:     " + runtimeBean.getVmName());
        System.out.println("VM Version:  " + runtimeBean.getVmVersion());
        System.out.printf("Uptime:      %d seconds%n", runtimeBean.getUptime() / 1000);
        System.out.println("Input args:  " + runtimeBean.getInputArguments());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. ALLOCATION HOTSPOT SIMULATION
    // ─────────────────────────────────────────────────────────────────────────

    static void demonstrateAllocationPressure() {
        System.out.println("\n─ Allocation pressure demo ───────────────────");
        long before = getUsedHeap();

        // High allocation: creates 1 million String objects
        List<String> strings = new ArrayList<>(1_000_000);
        for (int i = 0; i < 1_000_000; i++) {
            strings.add("item-" + i);  // String concatenation allocates
        }

        long after = getUsedHeap();
        System.out.printf("Heap used before: %,d MB  after: %,d MB  delta: %,d MB%n",
                before / (1024 * 1024), after / (1024 * 1024),
                (after - before) / (1024 * 1024));

        // Now let GC clean up
        strings = null;
        System.gc();
        System.out.printf("After GC: %,d MB%n", getUsedHeap() / (1024 * 1024));
    }

    private static long getUsedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════ JVM Diagnostics Demo ═══════════");

        printRuntimeInfo();
        printMemoryStats();
        printGcStats();
        inspectClassLoading();
        inspectThreads();
        demonstrateReferences();
        demonstrateAllocationPressure();
        printGcStats();  // compare GC stats after allocation demo
    }
}
