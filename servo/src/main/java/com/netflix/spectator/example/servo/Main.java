package com.netflix.spectator.example.servo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.iep.guice.GuiceHelper;
import com.netflix.servo.publish.AsyncMetricObserver;
import com.netflix.servo.publish.BasicMetricFilter;
import com.netflix.servo.publish.CounterToRateMetricTransform;
import com.netflix.servo.publish.FileMetricObserver;
import com.netflix.servo.publish.MetricObserver;
import com.netflix.servo.publish.MetricPoller;
import com.netflix.servo.publish.MonitorRegistryMetricPoller;
import com.netflix.servo.publish.PollRunnable;
import com.netflix.servo.publish.PollScheduler;
import com.netflix.servo.publish.atlas.AtlasMetricObserver;
import com.netflix.servo.publish.atlas.ServoAtlasConfig;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.example.Server;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;
import com.netflix.spectator.servo.ServoRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final GcLogger GC_LOGGER = new GcLogger();

  private static final long POLL_INTERVAL = 10;

  private static final String CLUSTER = "nf.cluster";
  private static final String NODE = "nf.node";
  private static final String UNKNOWN = "unknown";

  private static MetricObserver rateTransform(MetricObserver observer) {
    final long heartbeat = 2 * POLL_INTERVAL;
    return new CounterToRateMetricTransform(observer, heartbeat, TimeUnit.SECONDS);
  }

  private static MetricObserver async(String name, MetricObserver observer) {
    final long expireTime = 2000 * POLL_INTERVAL;
    final int queueSize = 10;
    return new AsyncMetricObserver(name, observer, queueSize, expireTime);
  }

  private static MetricObserver createFileObserver(File dir) {
    if (!dir.mkdirs() && !dir.isDirectory())
      throw new IllegalStateException("failed to create metrics directory: " + dir);
    return rateTransform(new FileMetricObserver("servo-example", dir));
  }

  private static TagList getCommonTags() {
    final Map<String, String> tags = new HashMap<>();
    final String cluster = System.getenv("NETFLIX_CLUSTER");
    tags.put(CLUSTER, (cluster == null) ? UNKNOWN : cluster);
    try {
      tags.put(NODE, InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
      tags.put(NODE, UNKNOWN);
    }
    return BasicTagList.copyOf(tags);
  }

  private static MetricObserver createAtlasObserver() {
    final ServoAtlasConfig cfg = Config.getAtlasConfig();
    final TagList common = getCommonTags();
    return rateTransform(async("atlas", new AtlasMetricObserver(cfg, common)));
  }

  private static void schedule(MetricPoller poller, List<MetricObserver> observers) {
    final PollRunnable task = new PollRunnable(poller, BasicMetricFilter.MATCH_ALL,
        true, observers);
    PollScheduler.getInstance().addPoller(task, 10, TimeUnit.SECONDS);
  }

  private static void initMetricsExtensions() throws Exception {
    if (Config.isGcExtEnabled()) {
      LOGGER.info("garbage collection extension enabled");
      GC_LOGGER.start(null);
    }

    if (Config.isJvmExtEnabled()) {
      LOGGER.info("jvm extension enabled");
      Jmx.registerStandardMXBeans(Spectator.globalRegistry());
    }
  }

  private static void initMetricsPublishing() throws Exception {
    final List<MetricObserver> observers = new ArrayList<>();

    if (Config.isFileObserverEnabled()) {
      final File dir = Config.getFileObserverDirectory();
      LOGGER.info("file observer enabled, logging to: " + dir);
      observers.add(createFileObserver(dir));
    }

    if (Config.isAtlasObserverEnabled()) {
      final String uri = Config.getAtlasObserverUri();
      LOGGER.info("atlas observer enabled, uri: " + uri);
      observers.add(createAtlasObserver());
    }

    PollScheduler.getInstance().start();
    schedule(new MonitorRegistryMetricPoller(), observers);
  }

  public static void main(String[] args) throws Exception {
    initMetricsExtensions();
    initMetricsPublishing();

    GuiceHelper helper = new GuiceHelper();
    helper.start(new AbstractModule() {
      @Override protected void configure() {
        bind(Clock.class).toInstance(Clock.SYSTEM);
        bind(Server.class).asEagerSingleton();
      }

      @Provides
      @Singleton
      private Registry providesRegistry(Clock clock) {
        Registry r = new ServoRegistry(clock);
        Spectator.globalRegistry().add(r);
        return r;
      }
    });

    helper.addShutdownHook();
  }
}
