package org.robolectric.internal;

import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.internal.bytecode.InstrumentingClassLoader;
import org.robolectric.internal.bytecode.Interceptors;
import org.robolectric.internal.bytecode.InvokeDynamicSupport;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.util.Pair;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.robolectric.util.ReflectionHelpers.newInstance;
import static org.robolectric.util.ReflectionHelpers.setStaticField;

public class InstrumentingClassLoaderFactory {

  /** The factor for cache size. See {@link #CACHE_SIZE} for details. */
  private static final int CACHE_SIZE_FACTOR = 3;

  /** We need to set the cache size of class loaders more than the number of supported APIs as different tests may have different configurations. */
  private static final int CACHE_SIZE = SdkConfig.getSupportedApis().size() * CACHE_SIZE_FACTOR;

  // Simple LRU Cache. SdkEnvironments are unique across InstrumentingClassloaderConfig and SdkConfig
  private static final LinkedHashMap<Pair<InstrumentationConfiguration, SdkConfig>, SdkEnvironment> sdkToEnvironment = new LinkedHashMap<Pair<InstrumentationConfiguration, SdkConfig>, SdkEnvironment>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Pair<InstrumentationConfiguration, SdkConfig>, SdkEnvironment> eldest) {
      return size() > CACHE_SIZE;
    }
  };

  private final InstrumentationConfiguration instrumentationConfig;
  private final DependencyResolver dependencyResolver;
  private final Interceptors interceptors;

  public InstrumentingClassLoaderFactory(InstrumentationConfiguration instrumentationConfig, DependencyResolver dependencyResolver, Interceptors interceptors) {
    this.instrumentationConfig = instrumentationConfig;
    this.dependencyResolver = dependencyResolver;
    this.interceptors = interceptors;
  }

  public synchronized SdkEnvironment getSdkEnvironment(SdkConfig sdkConfig) {

    Pair<InstrumentationConfiguration, SdkConfig> key = Pair.create(instrumentationConfig, sdkConfig);

    SdkEnvironment sdkEnvironment = sdkToEnvironment.get(key);
    if (sdkEnvironment == null) {
      URL url = dependencyResolver.getLocalArtifactUrl(sdkConfig.getAndroidSdkDependency());

      ClassLoader robolectricClassLoader = new InstrumentingClassLoader(instrumentationConfig, url);
      sdkEnvironment = new SdkEnvironment(sdkConfig, robolectricClassLoader);

      setStaticField(sdkEnvironment.bootstrappedClass(InvokeDynamicSupport.class), "INTERCEPTORS",
          interceptors);
      setStaticField(sdkEnvironment.bootstrappedClass(Shadow.class), "SHADOW_IMPL",
          newInstance(sdkEnvironment.bootstrappedClass(ShadowImpl.class)));

      sdkToEnvironment.put(key, sdkEnvironment);
    }
    return sdkEnvironment;
  }
}
