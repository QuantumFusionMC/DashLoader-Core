package net.oskarstrom.dashloader.api;

import net.oskarstrom.dashloader.api.registry.DashRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadManager {
	private static final int THRESHOLD = 100;
	@Nullable
	private static ForkJoinPool dashExecutionPool;

	public static void init() {
		dashExecutionPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), new ForkJoinPool.ForkJoinWorkerThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);

			@Override
			public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
				final ForkJoinWorkerThread dashThread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
				dashThread.setDaemon(true);
				dashThread.setName("dashloaderc-thread-" + threadNumber.getAndIncrement());
				return dashThread;
			}
		}, (t, e) -> {
			System.out.println(t.getName() + " failed, ERROR: " + e.getMessage());
			e.printStackTrace();
		}, true);
	}

	public static <F, D extends Dashable<F>> void parallelToUndash(DashRegistry registry, D[] dashArray, F[] outputArray) {
		ensureReadyForExecution();
		//noinspection ConstantConditions
		dashExecutionPool.invoke(new UndashTask<>(registry, dashArray, outputArray));
	}

	public static <O, C extends Callable<O>> List<O> executeCallables(C... callables) throws ExecutionException, InterruptedException {
		return executeCallables(Arrays.stream(callables).toList());
	}

	public static <O, C extends Callable<O>> List<O> executeCallables(List<C> callables) throws ExecutionException, InterruptedException {
		ensureReadyForExecution();
		//noinspection ConstantConditions
		final List<Future<O>> futures = dashExecutionPool.invokeAll(callables);
		List<O> output = new ArrayList<>();
		for (Future<O> future : futures)
			output.add(future.get());
		return output;
	}


	public static <R extends Runnable> void executeRunnables(R... runnables) throws ExecutionException, InterruptedException {
		executeRunnables(Arrays.stream(runnables).toList());
	}

	public static <R extends Runnable> void executeRunnables(List<R> runnables) throws ExecutionException, InterruptedException {
		ensureReadyForExecution();
		//noinspection ConstantConditions
		final List<Future<Object>> futures = dashExecutionPool.invokeAll(runnables.stream().map(Executors::callable).toList());
		for (Future<Object> future : futures)
			future.get();
	}

	public static <D extends Applyable> void parallelApply(DashRegistry registry, D[] applyArray) {
		ensureReadyForExecution();
		//noinspection ConstantConditions
		dashExecutionPool.invoke(new ApplyTask<>(registry, applyArray));
	}

	private static void ensureReadyForExecution() {
		if (dashExecutionPool == null || dashExecutionPool.isTerminated()) {
			throw new NullPointerException("ThreadPool not initialized");
		}
	}

	public static class UndashTask<D extends Dashable<F>, F> extends RecursiveAction {
		private final int start;
		private final int stop;
		private final D[] startArray;
		private final F[] outputArray;
		private final DashRegistry registry;

		public UndashTask(DashRegistry registry, D[] startArray, F[] outputArray) {
			this.registry = registry;
			this.start = 0;
			this.stop = startArray.length;
			this.startArray = startArray;
			this.outputArray = outputArray;
		}

		private UndashTask(DashRegistry registry, int start, int stop, D[] startArray, F[] outputArray) {
			this.registry = registry;
			this.start = start;
			this.stop = stop;
			this.startArray = startArray;
			this.outputArray = outputArray;
		}

		@Override
		protected void compute() {
			final int size = stop - start;
			if (size < THRESHOLD) {
				computeTask();
			} else {
				final int middle = start + (size / 2);
				final UndashTask<D, F> alpha = new UndashTask<>(registry, start, middle, startArray, outputArray);
				final UndashTask<D, F> beta = new UndashTask<>(registry, middle, stop, startArray, outputArray);
				invokeAll(alpha, beta);
			}
		}

		private void computeTask() {
			for (int i = start; i < stop; i++)
				outputArray[i] = startArray[i].toUndash(registry);
		}
	}

	public static class ApplyTask<D extends Applyable> extends RecursiveAction {
		private final int start;
		private final int stop;
		private final D[] startArray;
		private final DashRegistry registry;

		public ApplyTask(DashRegistry registry, D[] startArray) {
			this.registry = registry;
			this.start = 0;
			this.stop = startArray.length;
			this.startArray = startArray;
		}

		private ApplyTask(DashRegistry registry, int start, int stop, D[] startArray) {
			this.registry = registry;
			this.start = start;
			this.stop = stop;
			this.startArray = startArray;
		}

		@Override
		protected void compute() {
			final int size = stop - start;
			if (size < THRESHOLD) {
				computeTask();
			} else {
				final int middle = start + (size / 2);
				final ApplyTask<D> alpha = new ApplyTask<>(registry, start, middle, startArray);
				final ApplyTask<D> beta = new ApplyTask<>(registry, middle, stop, startArray);
				invokeAll(alpha, beta);
			}
		}

		private void computeTask() {
			for (int i = start; i < stop; i++)
				startArray[i].apply(registry);
		}
	}
}