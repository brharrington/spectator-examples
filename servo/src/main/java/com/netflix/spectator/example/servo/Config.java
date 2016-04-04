package com.netflix.spectator.example.servo;

import com.netflix.servo.monitor.Pollers;
import com.netflix.servo.publish.atlas.ServoAtlasConfig;

import java.io.File;

/**
 * Utility class dealing with different settings used to run the examples.
 */
public final class Config {
  private Config() {
  }

  /**
   * Port number for the http server to listen on.
   */
  public static int getPort() {
    return Integer.parseInt(System.getProperty("spectator.example.port", "12345"));
  }

  /**
   * How frequently to poll metrics in seconds and report to observers.
   */
  public static long getPollInterval() {
    return Pollers.getPollingIntervals().get(0) / 1000L;
  }

  /**
   * Should we report metrics to the file observer? Default is true.
   */
  public static boolean isFileObserverEnabled() {
    return Boolean.valueOf(System.getProperty("spectator.example.fileObserverEnabled", "true"));
  }

  /**
   * Default directory for writing metrics files. Default is ./build/metrics.
   */
  public static File getFileObserverDirectory() {
    return new File(System.getProperty("spectator.example.fileObserverDirectory", "./build/metrics"));
  }

  /**
   * Should we report metrics to atlas? Default is false.
   */
  public static boolean isAtlasObserverEnabled() {
    return Boolean.valueOf(System.getProperty("spectator.example.atlasObserverEnabled", "false"));
  }

  /**
   * URI for Atlas backend. Default is http://localhost:7101/api/v1/publish.
   */
  public static String getAtlasObserverUri() {
    return System.getProperty("spectator.example.atlasObserverUri",
        "http://localhost:7101/api/v1/publish");
  }

  /**
   * Should we enable the jvm ext metrics? Default is true.
   */
  public static boolean isJvmExtEnabled() {
    return Boolean.valueOf(System.getProperty("spectator.example.jvmExtEnabled", "true"));
  }

  /**
   * Should we enable the jvm gc metrics? Default is true.
   */
  public static boolean isGcExtEnabled() {
    return Boolean.valueOf(System.getProperty("spectator.example.gcExtEnabled", "true"));
  }

  /**
   * Get config for the atlas observer.
   */
  public static ServoAtlasConfig getAtlasConfig() {
    return new ServoAtlasConfig() {
      @Override
      public String getAtlasUri() {
        return getAtlasObserverUri();
      }

      @Override
      public int getPushQueueSize() {
        return 1000;
      }

      @Override
      public boolean shouldSendMetrics() {
        return isAtlasObserverEnabled();
      }

      @Override
      public int batchSize() {
        return 10000;
      }
    };
  }
}