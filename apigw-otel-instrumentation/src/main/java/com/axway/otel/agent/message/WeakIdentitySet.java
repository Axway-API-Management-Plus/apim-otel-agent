package com.axway.otel.agent.message;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

public final class WeakIdentitySet<T> {
	private static final class Key<T> extends WeakReference<T> {
		private final int hash;

		private Key(T referent, ReferenceQueue<T> queue) {
			super(referent, queue);

			this.hash = System.identityHashCode(referent);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (!(obj instanceof Key)) {
				return false;
			}

			T referent = get();

			return (referent != null) && (referent == ((Key<?>) obj).get());
		}
	}

	private final ReferenceQueue<T> stale = new ReferenceQueue<T>();
	private final Set<Key<T>> keys = new HashSet<Key<T>>();

	private void expunge() {
		Reference<? extends T> ref = null;

		while ((ref = stale.poll()) != null) {
			keys.remove(ref);
		}
	}

	public boolean add(T value) {
		expunge();

		if (value == null) {
			return false;
		}

		if (keys.contains(new Key<T>(value, null))) {
			return false;
		}

		return keys.add(new Key<T>(value, stale));
	}

	public void clear() {
		keys.clear();

		expunge();
	}
}
