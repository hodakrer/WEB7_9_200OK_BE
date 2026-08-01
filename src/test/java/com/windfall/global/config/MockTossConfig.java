package com.windfall.global.config;

import java.io.IOException;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class MockTossConfig {

  @Bean
  public MockWebServer mockTossServer() throws IOException {
    MockWebServer server = new MockWebServer();
    server.start();
    return server;
  }

  @Bean
  @Primary
  public WebClient mockTossWebClient(MockWebServer mockTossServer) {
    return WebClient.builder()
        .baseUrl(mockTossServer.url("/").toString())
        .build();
  }
}
