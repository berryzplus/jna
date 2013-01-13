package com.sun.jna;
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Modified to remove Genercs for JNA.
 */

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * Implements a combination of WeakHashMap and IdentityHashMap.
 * Useful for caches that need to key off of a == comparison
 * instead of a .equals.
 *
 * <b>
 * This class is not a general-purpose Map implementation! While
 * this class implements the Map interface, it intentionally violates
 * Map's general contract, which mandates the use of the equals method
 * when comparing objects. This class is designed for use only in the
 * rare cases wherein reference-equality semantics are required.
 *
 * Note that this implementation is not synchronized.
 * </b>
 */
public class WeakIdentityHashMap implements Map {
    private final ReferenceQueue queue = new ReferenceQueue();
    private final Map backingStore = new HashMap();


    public WeakIdentityHashMap() {
    }


    public void clear() {
        backingStore.clear();
        reap();
    }

    public boolean containsKey(final Object key) {
        reap();
        return backingStore.containsKey(new IdentityWeakReference(key));
    }

    public boolean containsValue(final Object value)  {
        reap();
        return backingStore.containsValue(value);
    }

    public Set entrySet() {
        reap();
        final Set ret = new HashSet();
        for (final Iterator i=backingStore.entrySet().iterator();i.hasNext();) {
            final Map.Entry ref = (Map.Entry)i.next();
            final Object key = ((IdentityWeakReference)ref.getKey()).get();
            final Object value = ref.getValue();
            final Map.Entry entry = new Map.Entry() {
                public Object getKey() {
                    return key;
                }
                public Object getValue() {
                    return value;
                }
                public Object setValue(final Object value) {
                    throw new UnsupportedOperationException();
                }
            };
            ret.add(entry);
        }
        return Collections.unmodifiableSet(ret);
    }
    public Set keySet() {
        reap();
        final Set ret = new HashSet();
        for (final Iterator i=backingStore.keySet().iterator();i.hasNext();) {
            final IdentityWeakReference ref = (IdentityWeakReference)i.next();
            ret.add(ref.get());
        }
        return Collections.unmodifiableSet(ret);
    }

    public boolean equals(final Object o) {
        if (!(o instanceof WeakIdentityHashMap))
            return false;
        return backingStore.equals(((WeakIdentityHashMap) o).backingStore);
    }

    public Object get(final Object key) {
        reap();
        return backingStore.get(new IdentityWeakReference(key));
    }
    public Object put(final Object key, final Object value) {
        reap();
        return backingStore.put(new IdentityWeakReference(key), value);
    }

    public int hashCode() {
        reap();
        return backingStore.hashCode();
    }
    public boolean isEmpty() {
        reap();
        return backingStore.isEmpty();
    }
    public void putAll(final Map t) {
        throw new UnsupportedOperationException();
    }
    public Object remove(final Object key) {
        reap();
        return backingStore.remove(new IdentityWeakReference(key));
    }
    public int size() {
        reap();
        return backingStore.size();
    }
    public Collection values() {
        reap();
        return backingStore.values();
    }

    private synchronized void reap() {
        Object zombie = queue.poll();

        while (zombie != null) {
            final IdentityWeakReference victim = (IdentityWeakReference)zombie;
            backingStore.remove(victim);
            zombie = queue.poll();
        }
    }

    class IdentityWeakReference extends WeakReference {
        int hash;

        //@SuppressWarnings("unchecked")
        IdentityWeakReference(final Object obj) {
            super(obj, queue);
            hash = System.identityHashCode(obj);
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(final Object o) {
            if (this == o)
                return true;
            if (!(o instanceof IdentityWeakReference))
                return false;
            return this.get() == ((IdentityWeakReference) o).get();
        }
    }
}




