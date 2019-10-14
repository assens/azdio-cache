package com.azdio.mdw.hazelcast.listeners;

import static com.azdio.mdw.hazelcast.listeners.CacheEntryListenersProvider.getCacheEntryCreatedListeners;
import static com.azdio.mdw.hazelcast.listeners.CacheEntryListenersProvider.getCacheEntryExpiredListener;
import static com.azdio.mdw.hazelcast.listeners.CacheEntryListenersProvider.getCacheEntryRemovedListener;
import static com.azdio.mdw.hazelcast.listeners.CacheEntryListenersProvider.getCacheEntryUpdatedListener;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

public class CompositeCacheEntryListener implements
    CacheEntryCreatedListener<Object, Object>,
    CacheEntryUpdatedListener<Object, Object>,
    CacheEntryExpiredListener<Object, Object>,
    CacheEntryRemovedListener<Object, Object> {

  @Override
  public void onCreated(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    getCacheEntryCreatedListeners().forEach(listener -> listener.onCreated(events));
  }

  @Override
  public void onUpdated(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    getCacheEntryUpdatedListener().forEach(listener -> listener.onUpdated(events));
  }

  @Override
  public void onRemoved(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    getCacheEntryRemovedListener().forEach(listener -> listener.onRemoved(events));
  }

  @Override
  public void onExpired(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
   getCacheEntryExpiredListener().forEach(listener -> listener.onExpired(events));
  }

}
