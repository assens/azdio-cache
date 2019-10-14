package com.azdio.cache.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.config.CachePartitionLostListenerConfig;
import com.hazelcast.config.CacheSimpleConfig;
import com.hazelcast.config.CacheSimpleEntryListenerConfig;

public interface CacheSimpleConfigMixIn {

  @JsonIgnore
  CacheSimpleConfig getAsReadOnly();

  @JsonIgnore
  List<CacheSimpleEntryListenerConfig> getCacheEntryListeners();

  @JsonIgnore
  List<CachePartitionLostListenerConfig> getPartitionLostListenerConfigs();
}
