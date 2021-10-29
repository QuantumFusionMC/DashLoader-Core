package dev.quantumfusion.dashloader.core.util;

import dev.quantumfusion.dashloader.core.Dashable;
import dev.quantumfusion.dashloader.core.registry.DashRegistryReader;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class DashThreading {
	public static final int CORES = Runtime.getRuntime().availableProcessors();
	private static ForkJoinPool THREAD_POOL;

	public static void init() {
		THREAD_POOL = new DashThreadPool();
	}

	private static void ensurePoolAlive() {
		if (THREAD_POOL == null || THREAD_POOL.isTerminated())
			throw new NullPointerException("ThreadPool not initialized");
	}

	private static Collection<Callable<Object>> convert(Collection<Runnable> runnables) {
		return runnables.stream().map(Executors::callable).toList();
	}

	public static void run(Runnable... runnables) {
		run(List.of(runnables));
	}

	public static void run(Collection<Runnable> runnables) {
		final List<Future<Object>> futures = THREAD_POOL.invokeAll(convert(runnables));
		for (Future<Object> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	public static <R, D extends Dashable<R>> void export(D[] dashables, Object[] data, DashRegistryReader registry) {
		ensurePoolAlive();
		THREAD_POOL.invoke(new UndashTask<>(dashables, data, registry));
	}

	public static <R, D extends Dashable<R>> void export(DashableEntry<D>[] dashables, Object[] data, DashRegistryReader registry) {
		ensurePoolAlive();
		THREAD_POOL.invoke(new PositionedUndashTask<>(dashables, data, registry));
	}


}