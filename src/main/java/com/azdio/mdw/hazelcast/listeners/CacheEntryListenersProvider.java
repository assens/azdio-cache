package com.azdio.mdw.hazelcast.listeners;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CacheEntryListenersProvider {

  private static List<CacheEntryCreatedListener<Object, Object>> cacheEntryCreatedListeners = new ArrayList<>();
  private static List<CacheEntryUpdatedListener<Object, Object>> cacheEntryUpdatedListener = new ArrayList<>();
  private static List<CacheEntryExpiredListener<Object, Object>> cacheEntryExpiredListener = new ArrayList<>();
  private static List<CacheEntryRemovedListener<Object, Object>> cacheEntryRemovedListener = new ArrayList<>();

  @Autowired
  List<CacheEntryCreatedListener<Object, Object>> createdListeners;
  @Autowired
  List<CacheEntryUpdatedListener<Object, Object>> updatedListeners;
  @Autowired
  List<CacheEntryExpiredListener<Object, Object>> expiredListeners;
  @Autowired
  List<CacheEntryRemovedListener<Object, Object>> removedListeners;

  @PostConstruct
  public void init() {
    cacheEntryCreatedListeners.addAll(createdListeners);
    cacheEntryUpdatedListener.addAll(updatedListeners);
    cacheEntryExpiredListener.addAll(expiredListeners);
    cacheEntryRemovedListener.addAll(removedListeners);
  }

  public static List<CacheEntryCreatedListener<Object, Object>> getCacheEntryCreatedListeners() {
    return cacheEntryCreatedListeners;
  }

  public static List<CacheEntryUpdatedListener<Object, Object>> getCacheEntryUpdatedListener() {
    return cacheEntryUpdatedListener;
  }

  public static List<CacheEntryExpiredListener<Object, Object>> getCacheEntryExpiredListener() {
    return cacheEntryExpiredListener;
  }

  public static List<CacheEntryRemovedListener<Object, Object>> getCacheEntryRemovedListener() {
    return cacheEntryRemovedListener;
  }
}
