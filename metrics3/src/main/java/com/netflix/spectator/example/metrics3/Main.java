package com.netflix.spectator.example.metrics3;

import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.iep.guice.GuiceHelper;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.example.Server;
import com.netflix.spectator.metrics3.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  @Singleton
  private static class ReportersManager {
    private final JmxReporter jmx;
    private final CsvReporter csv;

    @Inject
    ReportersManager(MetricRegistry codaRegistry) {
      jmx = JmxReporter.forRegistry(codaRegistry).build();
      jmx.start();

      File dir = new File("./metrics");
      dir.mkdirs();
      csv = CsvReporter.forRegistry(codaRegistry).build(dir);
      csv.start(5, TimeUnit.SECONDS);
    }

    @PreDestroy
    private void shutdown() {
      jmx.stop();
      csv.stop();
    }
  }

  public static void main(String[] args) throws Exception {
    GuiceHelper helper = new GuiceHelper();
    helper.start(new AbstractModule() {
      @Override protected void configure() {
        bind(Clock.class).toInstance(Clock.SYSTEM);
        bind(Server.class).asEagerSingleton();
        bind(ReportersManager.class).asEagerSingleton();
      }

      @Provides
      @Singleton
      private MetricRegistry providesCodaRegistry() {
        return new MetricRegistry();
      }

      @Provides
      @Singleton
      private Registry providesRegistry(Clock clock, MetricRegistry codaRegistry) {
        return new MetricsRegistry(clock, codaRegistry);
      }
    });

    helper.addShutdownHook();
  }
}
