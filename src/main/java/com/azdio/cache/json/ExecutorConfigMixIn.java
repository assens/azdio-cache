package com.azdio.cache.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.config.ExecutorConfig;

public interface ExecutorConfigMixIn {

  @JsonIgnore
  ExecutorConfig getAsReadOnly();
}
