package com.azdio.cache;

import static com.hazelcast.config.EvictionConfig.MaxSizePolicy.ENTRY_COUNT;
import static java.util.Objects.nonNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.persistence.QueryHint;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.springframework.data.jpa.repository.QueryHints;

import com.azdio.cache.HazelcastConfiguration.CacheConfig;
import com.azdio.cache.HazelcastConfiguration.ManagementCenter;
import com.azdio.cache.json.CacheSimpleConfigMixIn;
import com.azdio.cache.json.ConfigMixIn;
import com.azdio.cache.json.DiscoveryConfigMixIn;
import com.azdio.cache.json.EvictionConfigMixIn;
import com.azdio.cache.json.ExecutorConfigMixIn;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.CacheSimpleConfig;
import com.hazelcast.config.CacheSimpleConfig.ExpiryPolicyFactoryConfig;
import com.hazelcast.config.CacheSimpleEntryListenerConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.DiscoveryConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.ExecutorConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.config.MapAttributeConfig;
import com.hazelcast.config.MapIndexConfig;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import lombok.Getter;

@Singleton
public class HazelcastService {

  private static final String PRINCIPAL_NAME_ATTRIBUTE = "principalName";

  @Getter
  private final HazelcastConfiguration hazelcastConfiguration;

  @Getter
  private HazelcastInstance hazelcastInstance;

  public HazelcastService(final HazelcastConfiguration hazelcastConfiguration,
      final ObjectMapper objectMapper) {
    this.hazelcastConfiguration = hazelcastConfiguration;
    objectMapper
        .addMixIn(Config.class, ConfigMixIn.class)
        .addMixIn(ExecutorConfig.class, ExecutorConfigMixIn.class)
        .addMixIn(DiscoveryConfig.class, DiscoveryConfigMixIn.class)
        .addMixIn(CacheSimpleConfig.class, CacheSimpleConfigMixIn.class)
        .addMixIn(EvictionConfig.class, EvictionConfigMixIn.class);
  }

  @EventListener
  public void onApplicationStartupEvent(final ApplicationStartupEvent applicationStartupEvent) {
    // NOP
  }

  @PostConstruct
  public void init() {
    final com.azdio.cache.HazelcastConfiguration.Config hzConfig = hazelcastConfiguration.getConfig();
    final Config config = new Config(hzConfig.getInstanceName());
    final NetworkConfig network = config.getNetworkConfig();
    final JoinConfig join = network.getJoin();
    final TcpIpConfig tcpIpConfig = join.getTcpIpConfig();
    final MulticastConfig multicastConfig = join.getMulticastConfig();

    network.setPort(hzConfig.getNetwork().getPort());
    network.setPortCount(hzConfig.getNetwork().getPortCount());
    network.setPortAutoIncrement(hzConfig.getNetwork().isPortAutoIncrement());
    network.getInterfaces()
        .setEnabled(true)
        .setInterfaces(hzConfig.getNetwork().getInterfaces());
    network.setPublicAddress(hzConfig.getNetwork().getPublicAddress());

    join.getAwsConfig().setEnabled(false);
    join.getAzureConfig().setEnabled(false);
    join.getEurekaConfig().setEnabled(false);
    join.getGcpConfig().setEnabled(false);
    join.getKubernetesConfig().setEnabled(false);

    multicastConfig.setEnabled(hzConfig.getNetwork().getJoin().getMulticast().isEnabled());

    tcpIpConfig.setEnabled(hzConfig.getNetwork().getJoin().getTcpIp().isEnabled());
    tcpIpConfig.setMembers(hzConfig.getNetwork().getJoin().getTcpIp().getMembers());

    config.setGroupConfig(new GroupConfig(hzConfig.getGroup().getName(), hzConfig.getGroup().getPassword()));

    hzConfig.getProperties().forEach((key, value) -> config.getProperties().setProperty((String) key, (String) value));

    final ManagementCenter managementCenter = hazelcastConfiguration.getManagementCenter();
    if (nonNull(managementCenter) && managementCenter.isEnabled()) {
      final ManagementCenterConfig managementCenterConfig = new ManagementCenterConfig();
      managementCenterConfig.setEnabled(true);
      managementCenterConfig.setUrl(managementCenter.getUrl());
      managementCenterConfig.setUpdateInterval(managementCenter.getUpdateInterval());
      config.setManagementCenterConfig(managementCenterConfig);
    }

    this.hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(config);

    final Reflections reflections = new Reflections("com.azdio.mdw.domain",
        new TypeAnnotationsScanner(),
        new SubTypesScanner(),
        new MethodAnnotationsScanner(),
        new FieldAnnotationsScanner());

    // Second Level Caches
    final Set<Class<?>> cacheableEntities = reflections.getTypesAnnotatedWith(javax.persistence.Cacheable.class);
    cacheableEntities.forEach(entityClass -> config.addCacheConfig(entityCacheSimpleConfig(entityClass)));

    // Query Caches
    final Set<Method> hintMethods = reflections.getMethodsAnnotatedWith(QueryHints.class);
    hintMethods.stream()
        .map(this::hintCacheSimpleConfig)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(config::addCacheConfig);

    // // Hibernate Query Caches
    // // http://docs.jboss.org/hibernate/orm/5.3/userguide/html_single/Hibernate_User_Guide.html#caching-query
    config.addCacheConfig(cacheConfig("default-query-results-region"));
    config.addCacheConfig(defaultUpdateTimestampsCacheConfig());

    // Spring Session
    final MapAttributeConfig attributeConfig = new MapAttributeConfig()
        .setName(PRINCIPAL_NAME_ATTRIBUTE)
        .setExtractor("org.springframework.session.hazelcast.PrincipalNameExtractor");
    config.getMapConfig("spring:session:sessions:nb")
        .addMapAttributeConfig(attributeConfig)
        .addMapIndexConfig(new MapIndexConfig(PRINCIPAL_NAME_ATTRIBUTE, false));
    config.getMapConfig("spring:session:sessions:weiss")
        .addMapAttributeConfig(attributeConfig)
        .addMapIndexConfig(new MapIndexConfig(PRINCIPAL_NAME_ATTRIBUTE, false));
    config.getMapConfig("spring:session:sessions:craft")
        .addMapAttributeConfig(attributeConfig)
        .addMapIndexConfig(new MapIndexConfig(PRINCIPAL_NAME_ATTRIBUTE, false));

    // User playing devices data (VodTracking)
    config.getMapConfig("PlayingDevices.*")
        .setMaxIdleSeconds(1800); // keep the data for half an hour

  }

  @PreDestroy
  public void destroy() {
    hazelcastInstance.shutdown();
  }

  public Set<String> getCacheNames() {
    return hazelcastInstance.getConfig().getCacheConfigs().keySet();
  }

  public Map<String, CacheSimpleConfig> getCacheConfigs() {
    return hazelcastInstance.getConfig().getCacheConfigs();
  }

  private CacheSimpleConfig entityCacheSimpleConfig(final Class<?> entityClass) {
    final String cacheName = entityClass.getCanonicalName();
    return cacheConfig(cacheName);
  }

  private CacheSimpleConfig cacheConfig(final String cacheName) {
    final CacheConfig cacheConfig = hazelcastConfiguration.getCacheConfig().getOrDefault(cacheName, hazelcastConfiguration.getCacheConfig().get(
        HazelcastConfiguration.DEFAULT));
    final CacheSimpleConfig cacheSimpleConfig = new CacheSimpleConfig()
        .setName(cacheName)
        .setManagementEnabled(cacheConfig.isManagementEnabled())
        .setStatisticsEnabled(cacheConfig.isStatisticsEnabled())
        .setReadThrough(cacheConfig.isReadThrough())
        .setWriteThrough(cacheConfig.isWriteThrough())
        .setEvictionConfig(new EvictionConfig(cacheConfig.getSize(), ENTRY_COUNT, cacheConfig.getEvictionPolicy()))
        .setExpiryPolicyFactoryConfig(expiryPolicyFactoryConfig(cacheConfig.getDurationAmount(), cacheConfig.getTimeUnit()))
        .setBackupCount(cacheConfig.getBackupCount())
        .setAsyncBackupCount(cacheConfig.getAsyncBackupCount());

    if (StringUtils.hasText(cacheConfig.getCacheEntryListenerFactory())) {
      final CacheSimpleEntryListenerConfig listenerConfig = new CacheSimpleEntryListenerConfig();
      listenerConfig.setCacheEntryListenerFactory(cacheConfig.getCacheEntryListenerFactory());
      cacheSimpleConfig.addEntryListenerConfig(listenerConfig);
    }
    return cacheSimpleConfig;
  }

  private ExpiryPolicyFactoryConfig expiryPolicyFactoryConfig(final long durationAmount, final TimeUnit timeUnit) {
    return new CacheSimpleConfig.ExpiryPolicyFactoryConfig(
        new CacheSimpleConfig.ExpiryPolicyFactoryConfig.TimedExpiryPolicyFactoryConfig(
            CacheSimpleConfig.ExpiryPolicyFactoryConfig.TimedExpiryPolicyFactoryConfig.ExpiryPolicyType.CREATED,
            new CacheSimpleConfig.ExpiryPolicyFactoryConfig.DurationConfig(durationAmount, timeUnit)));
  }

  private Optional<CacheSimpleConfig> hintCacheSimpleConfig(final Method method) {
    final QueryHints hints = method.getAnnotation(QueryHints.class);
    final Optional<QueryHint> queryHint = Arrays.asList(hints.value()).stream()
        .filter(hint -> hint.name().equals(org.hibernate.annotations.QueryHints.CACHE_REGION))
        .findFirst();
    if (queryHint.isPresent()) {
      return Optional.of(cacheConfig(queryHint.get().value()));
    } else {
      return Optional.empty();
    }
  }

  private CacheSimpleConfig defaultUpdateTimestampsCacheConfig() {
    final String cacheName = "default-update-timestamps-region";
    final CacheSimpleConfig cacheConfig = cacheConfig(cacheName);
    cacheConfig.setExpiryPolicyFactoryConfig(expiryPolicyFactoryConfig(1, TimeUnit.DAYS));
    return cacheConfig;
  }
}
