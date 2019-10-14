package com.azdio.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.NetworkConfig;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

@Data
@ConfigurationProperties("hazelcast")
public class HazelcastConfiguration {

  public static final String DEFAULT = "DEFAULT";

  @Data
  @ConfigurationProperties("config")
  public static class Config {
    @Data
    @ConfigurationProperties("group")
    public static class Group {
      private String name;
      private String password;
    }

    @Data
    @ConfigurationProperties("network")
    public static class Network {
      @Data
      @ConfigurationProperties("join")
      public static class Join {
        @Data
        @ConfigurationProperties("multicast")
        public static class Multicast {
          private boolean enabled = false;
        }

        @Data
        @ConfigurationProperties("tcp-ip")
        public static class TcpIp {
          private int connectionTimeoutSeconds = 10;
          private boolean enabled = true;
          private List<String> members = new ArrayList<>();
          private String requiredMember;
        }

        private Multicast multicast = new Multicast();
        private TcpIp tcpIp = new TcpIp();
      }

      private int port = NetworkConfig.DEFAULT_PORT;
      private int portCount = 100;
      private boolean portAutoIncrement = true;
      private Set<String> interfaces = new HashSet<>();
      private String publicAddress;
      private Join join;

    }

    private String instanceName;
    private Group group;
    private Network network;
    private Properties properties;

  }

  @Data
  @ConfigurationProperties("management-center")
  public static class ManagementCenter {
    private boolean enabled = false;
    private String url;
    private int updateInterval = 5;
  }

  @Data
  public static class CacheConfig {
    private boolean statisticsEnabled = true;
    private boolean managementEnabled = true;
    private boolean readThrough = true;
    private boolean writeThrough = true;
    private int size = 50000;
    private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
    private int durationAmount = 30;
    private TimeUnit timeUnit = TimeUnit.MINUTES;
    private int backupCount = 0;
    private int asyncBackupCount = 0;
    private String cacheEntryListenerFactory = "com.azdio.mdw.hazelcast.listeners.HazelcastCacheEntryListenerFactory";

  }

  private Config config;
  private ManagementCenter managementCenter;
  private Map<String, CacheConfig> cacheConfig = new HashMap<>(Collections.singletonMap(DEFAULT, new CacheConfig()));

}
