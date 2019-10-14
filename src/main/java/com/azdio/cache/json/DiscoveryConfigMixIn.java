package com.azdio.cache.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.config.DiscoveryConfig;

public interface DiscoveryConfigMixIn {

  @JsonIgnore
  DiscoveryConfig getAsReadOnly();
}
