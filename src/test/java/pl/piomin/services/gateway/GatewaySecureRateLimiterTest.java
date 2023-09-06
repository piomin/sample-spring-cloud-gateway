package pl.piomin.services.gateway;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;
import pl.piomin.services.gateway.model.Account;

import java.util.Base64;
import java.util.Random;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"rateLimiter.secure=true"})
@RunWith(SpringRunner.class)
public class GatewaySecureRateLimiterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewaySecureRateLimiterTest.class);
    private Random random = new Random();

    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    @ClassRule
    public static MockServerContainer mockServer = new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));
    @ClassRule
    public static GenericContainer redis = new GenericContainer("redis:5.0.6").withExposedPorts(6379);

    @Autowired
    TestRestTemplate template;

    @BeforeClass
    public static void init() {
        System.setProperty("spring.cloud.gateway.routes[0].id", "account-service");
        System.setProperty("spring.cloud.gateway.routes[0].uri", "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/account/**");
        System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/account/(?<path>.*), /$\\{path}");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "RequestRateLimiter");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.replenishRate", "1");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.burstCapacity", "60");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.requestedTokens", "15");
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", "" + redis.getMappedPort(6379));
        new MockServerClient(mockServer.getHost(), mockServer.getServerPort())
                .when(HttpRequest.request()
                        .withPath("/1"))
                .respond(response()
                        .withBody("{\"id\":1,\"number\":\"1234567890\"}")
                        .withHeader("Content-Type", "application/json"));
    }

    @Test
    @BenchmarkOptions(warmupRounds = 0, concurrency = 1, benchmarkRounds = 20)
    public void testAccountService() {
        String username = "user" + (random.nextInt(3) + 1);
        HttpHeaders headers = createHttpHeaders(username,"1234");
        HttpEntity<String> entity = new HttpEntity<String>(headers);
        ResponseEntity<Account> r = template.exchange("/account/{id}", HttpMethod.GET, entity, Account.class, 1);
        LOGGER.info("Received({}): status->{}, payload->{}, remaining->{}",
                username, r.getStatusCodeValue(), r.getBody(), r.getHeaders().get("X-RateLimit-Remaining"));
    }

    private HttpHeaders createHttpHeaders(String user, String password) {
        String notEncoded = user + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(notEncoded.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Basic " + encodedAuth);
        return headers;
    }

}
