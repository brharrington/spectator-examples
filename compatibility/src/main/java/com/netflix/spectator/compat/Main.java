package com.netflix.spectator.compat;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.DoubleFunction;
import com.netflix.spectator.api.Functions;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.ValueFunction;
import com.netflix.spectator.api.histogram.BucketCounter;
import com.netflix.spectator.api.histogram.BucketDistributionSummary;
import com.netflix.spectator.api.histogram.BucketFunctions;
import com.netflix.spectator.api.histogram.BucketTimer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

/**
 * This is a simple class that uses a lot of the public API for spectator to help check for
 * problems with breaking changes. There are also some tools hooked into the build that provide
 * some checking, but they have missed things in the past. Since the API surface is fairly small
 * this provides a simple alternative that is pretty easy to control. However, it needs to be
 * manually updated for any changes.
 */
public class Main {

  private static Map<String, String> TAGS = new LinkedHashMap<>();
  static {
    TAGS.put("app",     "foo");
    TAGS.put("cluster", "foo-dev");
    TAGS.put("asg",     "foo-dev-v001");
    TAGS.put("node",    "i-12345");
  }

  private static void setElapsedTime(Registry r, long t) {
    ManualClock c = (ManualClock) r.clock();
    c.setMonotonicTime(t);
  }

  private static void updateElapsedTime(Registry r, long t) {
    setElapsedTime(r, r.clock().monotonicTime() + t);
  }

  private static void record(DistributionSummary s) {
    long startingCount = s.count();
    long startingAmount = s.totalAmount();
    s.record(42);
    s.record(42L);
    s.record(Integer.valueOf(42));
    s.record(Long.valueOf(42));
    s.record(0);
    assert s.count() == startingCount + 5;
    assert s.totalAmount() == startingAmount + (4 * 42);
  }

  private static void checkDistributionSummary(Registry registry) {
    record(registry.distributionSummary(registry.createId("dist")));
    record(registry.distributionSummary(registry.createId("dist").withTag("a", "b")));
    record(registry.distributionSummary(registry.createId("dist", "a", "b")));
    record(registry.distributionSummary("dist", "a", "b"));
  }

  private static void record(Timer t) throws Exception {
    long startingCount = t.count();
    long startingTime = t.totalTime();

    t.record(42, TimeUnit.MICROSECONDS);
    t.record(42, TimeUnit.HOURS);
    t.record(Integer.valueOf(42), TimeUnit.SECONDS);
    t.record(Long.valueOf(42), TimeUnit.SECONDS);

    t.record(new Runnable() {
      @Override public void run() {
      }
    });

    final String v = t.record(new Callable<String>() {
      @Override public String call() throws Exception {
        return "foo";
      }
    });
    assert "foo".equals(v);

    t.record(Main::methodToTime);
    assert "bar".equals(t.record(Main::getBar));

    assert t.count() == startingCount + 8;
    assert t.totalTime() == startingTime + 151284000042000L;
  }

  private static void methodToTime() {
  }

  private static String getBar() {
    return "bar";
  }

  private static void checkTimer(Registry registry) throws Exception {
    record(registry.timer(registry.createId("timer")));
    record(registry.timer(registry.createId("timer").withTag("a", "b")));
    record(registry.timer(registry.createId("timer", "a", "b")));
    record(registry.timer("timer", "a", "b"));
  }

  private static void record(Registry r, LongTaskTimer t) throws Exception {
    long task = t.start();
    try {
      assert t.duration(task) == 0L;
      updateElapsedTime(r, TimeUnit.NANOSECONDS.convert(42L, TimeUnit.MINUTES));
      assert t.duration(task) == TimeUnit.NANOSECONDS.convert(42L, TimeUnit.MINUTES);
    } finally {
      // Stop is purposely not called here so that when the values are written out they will
      // show an active task with 42 minute duration
      //t.stop(task);
    }
  }

  private static void checkLongTaskTimer(Registry registry) throws Exception {
    record(registry, registry.longTaskTimer(registry.createId("long-timer")));
    record(registry, registry.longTaskTimer(registry.createId("long-timer").withTag("a", "b")));
    record(registry, registry.longTaskTimer(registry.createId("long-timer", "a", "b")));
    record(registry, registry.longTaskTimer("long-timer", "a", "b"));
  }

  private static void record(Counter c) throws Exception {
    long startingCount = c.count();
    c.increment();
    c.increment(42);
    c.increment(Integer.valueOf(42));
    c.increment(Long.valueOf(42));
    assert c.count() == startingCount + (3 * 42 + 1);
  }

  private static void checkCounter(Registry registry) throws Exception {
    record(registry.counter(registry.createId("counter")));
    record(registry.counter(registry.createId("counter").withTag("a", "b")));
    record(registry.counter(registry.createId("counter", "a", "b")));
    record(registry.counter("counter", "a", "b"));
  }

  private static void checkBucketCounter(Registry registry, String mode) throws Exception {
    LongFunction<String> f = null;
    switch (mode) {
      case "age":             f = BucketFunctions.age(500, TimeUnit.MILLISECONDS);        break;
      case "ageBiasOld":      f = BucketFunctions.ageBiasOld(500, TimeUnit.MILLISECONDS); break;
      case "latency":         f = BucketFunctions.latency(500, TimeUnit.MILLISECONDS);    break;
      case "latencyBiasSlow": f = BucketFunctions.latency(500, TimeUnit.MILLISECONDS);    break;
      case "bytes":           f = BucketFunctions.bytes(500);                             break;
      case "decimal":         f = BucketFunctions.decimal(500);                           break;
      default: throw new IllegalStateException("unkown mode: " + mode);
    }
    BucketCounter bc = BucketCounter.get(registry, registry.createId("bucket-counter-" + mode), f);
    for (int i = 0; i < 1000; ++i) {
      bc.record(TimeUnit.MILLISECONDS.toNanos(i));
    }
  }

  private static void checkBucketDistributionSummary(Registry registry) throws Exception {
    final LongFunction<String> f = BucketFunctions.latencyBiasSlow(500, TimeUnit.MILLISECONDS);
    BucketDistributionSummary bds = BucketDistributionSummary
        .get(registry, registry.createId("bucket-dist"), f);
    for (int i = 0; i < 1000; ++i) {
      bds.record(TimeUnit.MILLISECONDS.toNanos(i));
    }
  }

  private static void checkBucketTimer(Registry registry) throws Exception {
    final LongFunction<String> f = BucketFunctions.age(500, TimeUnit.MILLISECONDS);
    BucketTimer bt = BucketTimer.get(registry, registry.createId("bucket-timer"), f);
    for (int i = 0; i < 1000; ++i) {
      bt.record(i, TimeUnit.MILLISECONDS);
    }
  }

  private static void checkGauge(Registry registry) throws Exception {
    registry.gauge(registry.createId("gauge"), new AtomicLong(7));
    registry.gauge(registry.createId("gauge").withTags(TAGS), new AtomicLong(7));

    AtomicLong value = registry.gauge("gauge", new AtomicLong(7));
    value.set(42);

    // Cast needed prior to 0.30.0
    registry.gauge("gauge-function", new AtomicLong(7), v -> ((AtomicLong) v).get() + 3);
    registry.gauge(registry.createId("gauge-function"), new AtomicLong(7), new ValueFunction() {
      @Override
      public double apply(Object ref) {
        return ((AtomicLong) ref).get() + 7;
      }
    });
    registry.gauge("gauge-function", new AtomicLong(11), v -> v.doubleValue());
    registry.gauge("gauge-function", new AtomicLong(13), new DoubleFunction() {
      @Override
      public double apply(double v) {
        return v + 17;
      }
    });

    registry.gauge("gauge-age-non-deterministic", new AtomicLong(7), Functions.AGE);

    ManualClock c = new ManualClock();
    c.setWallTime(56);
    registry.gauge("gauge-age", new AtomicLong(7), Functions.age(c));

    registry.methodValue("method-value", new AtomicLong(11), "get");
    registry.methodValue(registry.createId("method-value"), new AtomicLong(11), "get");

    registry.mapSize("map-size", TAGS);
    registry.mapSize(registry.createId("map-size"), TAGS);

    registry.collectionSize("collection-size", TAGS.values());
    registry.collectionSize(registry.createId("collection-size"), TAGS.keySet());
  }

  public static Collection<String> run() throws Exception {
    ManualClock clock = new ManualClock();
    clock.setWallTime(1234567890L);
    Registry r = new DefaultRegistry(clock);
    checkDistributionSummary(r);
    checkTimer(r);
    checkLongTaskTimer(r);
    checkCounter(r);
    checkGauge(r);

    // Histogram utilities
    checkBucketCounter(r, "age");
    checkBucketCounter(r, "ageBiasOld");
    checkBucketCounter(r, "latency");
    checkBucketCounter(r, "latencyBiasSlow");
    checkBucketCounter(r, "bytes");
    checkBucketCounter(r, "decimal");
    checkBucketDistributionSummary(r);
    checkBucketTimer(r);

    List<String> ms = new ArrayList<>();
    for (Meter meter : r) {
      for (Measurement m : meter.measure()) {
        ms.add(m.toString());
      }
    }
    Collections.sort(ms);
    return ms.stream()
        .filter(s -> !s.contains("non-deterministic"))
        .collect(Collectors.toList());
  }

  public static void main(String[] args) throws Exception {
    for (String s : run()) {
      System.out.println(s);
    }
  }

}
