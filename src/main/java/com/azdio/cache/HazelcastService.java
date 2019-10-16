package com.azdio.cache;

import static com.hazelcast.config.EvictionConfig.MaxSizePolicy.ENTRY_COUNT;
import static java.util.Objects.nonNull;
import static org.springframework.session.hazelcast.HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
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
import com.azdio.cache.HazelcastConfiguration.Config.Group;
import com.azdio.cache.HazelcastConfiguration.Config.Network.Interfaces;
import com.azdio.cache.HazelcastConfiguration.Config.Network.Join.Multicast;
import com.azdio.cache.HazelcastConfiguration.Config.Network.Join.TcpIp;
import com.azdio.cache.HazelcastConfiguration.ManagementCenter;
import com.azdio.cache.json.CacheSimpleConfigMixIn;
import com.azdio.cache.json.ConfigMixIn;
import com.azdio.cache.json.DiscoveryConfigMixIn;
import com.azdio.cache.json.EvictionConfigMixIn;
import com.azdio.cache.json.ExecutorConfigMixIn;
import com.azdio.mdw.domain.ImageEntity;
import com.azdio.mdw.domain.ImageEntityWithContent;
import com.azdio.mdw.domain.ImageEntityWithHash;
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

    final Interfaces interfaces = hzConfig.getNetwork().getInterfaces();
    network.getInterfaces()
        .setEnabled(interfaces.isEnabled());
    interfaces.getPublicAddress().forEach(ip -> network.getInterfaces().addInterface(ip));

    join.getAwsConfig().setEnabled(false);
    join.getAzureConfig().setEnabled(false);
    join.getEurekaConfig().setEnabled(false);
    join.getGcpConfig().setEnabled(false);
    join.getKubernetesConfig().setEnabled(false);

    final Multicast multicast = hzConfig.getNetwork().getJoin().getMulticast();
    multicastConfig
        .setEnabled(multicast.isEnabled())
        .setLoopbackModeEnabled(multicast.isLoopbackModeEnabled());

    final TcpIp tcpIp = hzConfig.getNetwork().getJoin().getTcpIp();
    tcpIpConfig
        .setEnabled(tcpIp.isEnabled())
        .setMembers(tcpIp.getMembers());

    final Group group = hzConfig.getGroup();
    config.setGroupConfig(new GroupConfig(group.getName(), group.getPassword()));

    hzConfig.getProperties().forEach((key, value) -> config.getProperties().setProperty((String) key, (String) value));

    final ManagementCenter managementCenter = hazelcastConfiguration.getManagementCenter();
    if (nonNull(managementCenter) && managementCenter.isEnabled()) {
      final ManagementCenterConfig managementCenterConfig = new ManagementCenterConfig();
      managementCenterConfig.setEnabled(true);
      managementCenterConfig.setUrl(managementCenter.getUrl());
      managementCenterConfig.setUpdateInterval(managementCenter.getUpdateInterval());
      config.setManagementCenterConfig(managementCenterConfig);
    }

    final Reflections reflections = new Reflections("com.azdio.mdw",
        new TypeAnnotationsScanner(),
        new SubTypesScanner(),
        new MethodAnnotationsScanner(),
        new FieldAnnotationsScanner());

    // Second Level Caches
    final Set<Class<?>> cacheableEntities = reflections.getTypesAnnotatedWith(javax.persistence.Cacheable.class);
    cacheableEntities.forEach(entityClass -> config.addCacheConfig(entityCacheSimpleConfig(entityClass)));

    // Collections fields cache
    final Set<Field> cachedFields = reflections.getFieldsAnnotatedWith(org.hibernate.annotations.Cache.class);
    cachedFields.stream()
        .forEach(field -> config.addCacheConfig(fieldCacheSimpleConfig(field)));

    // Query Caches
    final Set<Method> hintMethods = reflections.getMethodsAnnotatedWith(org.springframework.data.jpa.repository.QueryHints.class);
    hintMethods.stream()
        .map(this::hintCacheSimpleConfig)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(config::addCacheConfig);

    // Hibernate Query Caches
    // http://docs.jboss.org/hibernate/orm/5.3/userguide/html_single/Hibernate_User_Guide.html#caching-query
    config.addCacheConfig(cacheConfig("default-query-results-region"));
    config.addCacheConfig(defaultUpdateTimestampsCacheConfig());

    // Images cache
    final Set<Class<? extends ImageEntity>> imageEntities = new HashSet<>();
    imageEntities.addAll(reflections.getSubTypesOf(ImageEntityWithHash.class));
    imageEntities.addAll(reflections.getSubTypesOf(ImageEntityWithContent.class));
    imageEntities.forEach(imageEntity -> config.addCacheConfig(imageEntityCacheSimpleConfig(imageEntity)));

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

    this.hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(config);

  }

  @PreDestroy
  public void destroy() {
    // hazelcastInstance.shutdown(); No need.
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

  private CacheSimpleConfig fieldCacheSimpleConfig(final Field field) {
    final String cacheName = field.getDeclaringClass().getCanonicalName() + "." + field.getName();
    final CacheSimpleConfig cacheConfig = cacheConfig(cacheName);
    cacheConfig.setWriteThrough(false);
    return cacheConfig;
  }

  private CacheSimpleConfig imageEntityCacheSimpleConfig(final Class<? extends ImageEntity> imageEntity) {
    final String cacheName = "ImagesCache.".concat(imageEntity.getSimpleName());
    return cacheConfig(cacheName);
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
