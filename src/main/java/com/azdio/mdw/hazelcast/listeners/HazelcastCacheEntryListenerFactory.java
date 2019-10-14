package com.azdio.mdw.hazelcast.listeners;

import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryListener;

public class HazelcastCacheEntryListenerFactory implements Factory<CacheEntryListener<Object, Object>> {

  private static final long serialVersionUID = 1L;

  @Override
  public CacheEntryListener<Object, Object> create() {
    return new CompositeCacheEntryListener();
  }

}
