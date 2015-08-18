package com.netflix.spectator.example.servo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.iep.guice.GuiceHelper;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.example.Server;
import com.netflix.spectator.servo.ServoRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    GuiceHelper helper = new GuiceHelper();
    helper.start(new AbstractModule() {
      @Override protected void configure() {
        bind(Clock.class).toInstance(Clock.SYSTEM);
        bind(Server.class).asEagerSingleton();
      }

      @Provides
      @Singleton
      private Registry providesRegistry(Clock clock) {
        return new ServoRegistry(clock);
      }
    });

    helper.addShutdownHook();
  }
}
