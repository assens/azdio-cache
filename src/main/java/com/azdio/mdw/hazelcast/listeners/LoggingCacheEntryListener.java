package com.azdio.mdw.hazelcast.listeners;

import java.lang.reflect.Field;
import java.util.Objects;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;

import org.hibernate.cache.internal.QueryResultsCacheImpl;
import org.hibernate.cache.spi.QueryKey;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LoggingCacheEntryListener implements
    CacheEntryCreatedListener<Object, Object>,
    CacheEntryUpdatedListener<Object, Object>,
    CacheEntryExpiredListener<Object, Object>,
    CacheEntryRemovedListener<Object, Object> {

  @Override
  public void onCreated(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    if (log.isDebugEnabled()) {
      events.forEach(this::log);
    }
  }

  @Override
  public void onUpdated(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    if (log.isDebugEnabled()) {
      events.forEach(this::log);
    }
  }

  @Override
  public void onRemoved(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    if (log.isDebugEnabled()) {
      events.forEach(this::log);
    }
  }

  @Override
  public void onExpired(final Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
    if (log.isDebugEnabled()) {
      events.forEach(this::log);
    }
  }

  private void log(final CacheEntryEvent<? extends Object, ? extends Object> event) {
    if (log.isTraceEnabled() && event.getValue() instanceof AbstractReadWriteAccess.SoftLockImpl) {
      log.trace("[cache][{}][{}][key: {}][value: {}]", event.getSource().getName(), event.getEventType(), event.getKey(), event.getValue());
    } else {
      if (log.isDebugEnabled()) {
        if (event.getKey() instanceof QueryKey) {
          logQueryCacheEvent(event);
        } else {
          logCacheEvent(event);
        }
      }
    }
  }

  private void logCacheEvent(final CacheEntryEvent<? extends Object, ? extends Object> event) {
    if (event.getEventType().equals(EventType.EXPIRED) || event.getSource().getName().startsWith("ImagesCache")) {
      log.debug("[cache][{}][{}][key: {}]", event.getSource().getName(), event.getEventType(), event.getKey());
    } else {
      log.debug("[cache][{}][{}][key: {}][value: {}][old value: {}]",
          event.getSource().getName(), event.getEventType(), event.getKey(),
          event.getValue(), event.getOldValue());
    }
  }

  private void logQueryCacheEvent(final CacheEntryEvent<? extends Object, ? extends Object> event) {
    final QueryKey queryKey = (QueryKey) event.getKey();
    final QueryResultsCacheImpl.CacheItem result = (QueryResultsCacheImpl.CacheItem) event.getValue();
    if (Objects.nonNull(result)) {
      final Class<?>[] declaredClasses = QueryResultsCacheImpl.class.getDeclaredClasses();
      final Field results = declaredClasses[0].getDeclaredFields()[1];
      results.setAccessible(true);
      try {
        log.debug("[cache][{}][{}][key: {}][value: {}]",
            event.getSource().getName(), event.getEventType(),
            queryKey, results.get(result));
      } catch (IllegalArgumentException | IllegalAccessException e) {
        // NOP
      } catch (final Exception e) {
        log.error("[cache][error: {}]", e.getMessage());
        log.error(e.getMessage(), e);
      }
    } else {
      log.debug("[cache][{}][{}][key: {}]", event.getSource().getName(), event.getEventType(), queryKey);
    }
  }
}
