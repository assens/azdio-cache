package com.azdio.cache.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.config.CacheSimpleConfig;

public interface ConfigMixIn {
  @JsonIgnore
  CacheSimpleConfig getAsReadOnly();
}
