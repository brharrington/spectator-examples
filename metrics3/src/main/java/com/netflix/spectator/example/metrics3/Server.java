package com.netflix.spectator.example.metrics3;

import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Server implements HttpHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final String[] AGENTS = new String[] {
      "chrome", "firefox", "msie", "safari", "mozilla", "curl", "java", "python"
  };

  private static final int PORT = 54321;

  private final Random random = new Random();

  private final HttpServer httpServer;

  private final Registry registry;
  private final Timer requestLatency;
  private final DistributionSummary requestSize;

  @Inject
  public Server(Registry registry) throws IOException {
    this.registry = registry;
    requestLatency = registry.timer("server.requestLatency");
    requestSize = registry.distributionSummary("server.requestSize");

    ThreadPoolExecutor executor = (ThreadPoolExecutor)
        Executors.newFixedThreadPool(10, r -> new Thread(r, "HttpServer"));
    registry.gauge("server.threadsBusy", executor, ThreadPoolExecutor::getActiveCount);
    registry.gauge("server.threadsMax",  executor, ThreadPoolExecutor::getMaximumPoolSize);
    registry.gauge("server.queueSize",   executor, e -> e.getQueue().size());

    httpServer = HttpServer.create(new InetSocketAddress(PORT), 100);
    httpServer.setExecutor(executor);
    httpServer.createContext("/", this);
    httpServer.start();

    LOGGER.info("server started on port " + PORT);
  }

  @PreDestroy
  public void shutdown() {
    httpServer.stop(0);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    requestLatency.record(() -> {
      requestSize.record(getRequestSize(exchange));

      int status = getStatus();
      final Id requestCountId = registry.createId("server.requestCount")
          .withTag("method", exchange.getRequestMethod())
          .withTag("status", "" + status)
          .withTag("agent",  getUserAgent(exchange));
      registry.counter(requestCountId).increment();

      try {
        byte[] msg = ("status " + status + "\n").getBytes("UTF-8");
        exchange.sendResponseHeaders(status, msg.length);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(msg);
        }
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  private int getStatus() {
    double p = random.nextDouble();
    if (p < 0.8)
      return 200;
    else if (p < 0.95)
      return 400;
    else
      return 500;
  }

  private int getRequestSize(HttpExchange exchange) {
    String length = exchange.getRequestHeaders().getFirst("Content-Length");
    try {
      return (length == null) ? -1 : Integer.parseInt(length);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private String getUserAgent(HttpExchange exchange) {
    String rawAgent = exchange.getRequestHeaders().getFirst("User-Agent");
    return (rawAgent == null) ? "unknown" : getAgentName(rawAgent);
  }

  private String getAgentName(String rawAgent) {
    final String rawLower = rawAgent.toLowerCase();
    for (String agent : AGENTS) {
      if (rawLower.contains(agent)) return agent;
    }
    return "unknown";
  }
}
