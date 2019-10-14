package com.azdio.cache;

import java.util.Map;
import java.util.Set;

import com.hazelcast.config.CacheSimpleConfig;
import com.hazelcast.config.Config;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/hazelcast")
public class HazelcastController {

  private final HazelcastService hazelcastService;

  public HazelcastController(final HazelcastService hazelcastService) {
    this.hazelcastService = hazelcastService;
  }

  @Get(uri = "/config")
  public Config config() {
    return hazelcastService.getHazelcastInstance().getConfig();
  }

  @Get(uri = "/cache")
  public Set<String> cache() {
    return hazelcastService.getCacheNames();
  }

  @Get(uri = "/cache/config")
  public Map<String, CacheSimpleConfig> getCacheConfigs() {
    return hazelcastService.getCacheConfigs();
  }
}
