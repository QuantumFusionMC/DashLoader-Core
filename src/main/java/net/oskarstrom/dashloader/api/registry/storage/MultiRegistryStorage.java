package net.oskarstrom.dashloader.api.registry.storage;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.oskarstrom.dashloader.api.Dashable;
import net.oskarstrom.dashloader.api.registry.DashRegistry;
import net.oskarstrom.dashloader.api.registry.FactoryConstructor;
import net.oskarstrom.dashloader.core.registry.RegistryStorageImpl;

public class MultiRegistryStorage<F, D extends Dashable<F>> extends RegistryStorageImpl<F, D> {
	public final int priority;
	public final boolean staged;
	private final Object2ObjectMap<Class<F>, FactoryConstructor<F, D>> constructor;


	public MultiRegistryStorage(Object2ObjectMap<Class<F>, FactoryConstructor<F, D>> constructor, DashRegistry registry, int priority, boolean staged) {
		super(registry);
		this.constructor = constructor;
		this.priority = priority;
		this.staged = staged;
	}

	@Override
	public D create(F object, DashRegistry registry) {
		final FactoryConstructor<F, D> fdFactoryConstructor = constructor.get(object.getClass());
		if (fdFactoryConstructor == null) {
			//TODO error handling
			throw new IllegalStateException();
		}
		return fdFactoryConstructor.create(object, registry);
	}
}