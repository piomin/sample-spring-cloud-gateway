package pl.piomin.services.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.piomin.services.gateway.model.Account;

import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {
                    "rateLimiter.secure=true",
                    "spring.cloud.gateway.routes[0].id=account-service",
                    "spring.cloud.gateway.routes[0].predicates[0]=Path=/account/**",
                    "spring.cloud.gateway.routes[0].filters[0]=RewritePath=/account/(?<path>.*), /$\\{path}",
                    "spring.cloud.gateway.routes[0].filters[1].name=RequestRateLimiter",
                    "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.replenishRate=1",
                    "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.burstCapacity=60",
                    "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.requestedTokens=15"
                })
@Testcontainers
@AutoConfigureTestRestTemplate
public class GatewaySecureRateLimiterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewaySecureRateLimiterTest.class);

    @Container
    static MockServerContainer mockServer = new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:5.0.6").withExposedPorts(6379);

    static TestRestTemplate staticTemplate;

    @Autowired
    TestRestTemplate template;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeAll
    static void init() {
        try (MockServerClient client = new MockServerClient(mockServer.getHost(), mockServer.getServerPort())) {
            client.when(HttpRequest.request().withPath("/1"))
                    .respond(response()
                            .withBody("{\"id\":1,\"number\":\"1234567890\"}")
                            .withHeader("Content-Type", "application/json"));
        }
    }

    @Test
    void testAccountServiceBenchmark() throws RunnerException {
        staticTemplate = template;
        Options options = new OptionsBuilder()
                .include(AccountServiceBenchmark.class.getSimpleName())
                .warmupIterations(0)
                .measurementIterations(8)
                .threads(1)
                .forks(0)
                .build();
        new Runner(options).run();
    }

    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public static class AccountServiceBenchmark {

        @Benchmark
        public void testAccountService() {
            String username = "user" + (ThreadLocalRandom.current().nextInt(3) + 1);
            HttpEntity<String> entity = new HttpEntity<>(createHttpHeaders(username, "1234"));
            ResponseEntity<Account> r = staticTemplate.exchange(
                    "/account/{id}", HttpMethod.GET, entity, Account.class, 1);
            LOGGER.info("Received({}): status->{}, payload->{}, remaining->{}",
                    username, r.getStatusCode().value(), r.getBody(),
                    r.getHeaders().get("X-RateLimit-Remaining"));
        }

        private static HttpHeaders createHttpHeaders(String user, String password) {
            String encodedAuth = Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", "Basic " + encodedAuth);
            return headers;
        }
    }
}