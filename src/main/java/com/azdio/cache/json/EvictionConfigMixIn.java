package com.azdio.cache.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.config.EvictionConfig;

public interface EvictionConfigMixIn {
  @JsonIgnore
  EvictionConfig getAsReadOnly();
}
