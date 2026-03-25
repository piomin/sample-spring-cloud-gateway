package pl.piomin.services.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.piomin.services.gateway.model.Account;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                properties = {
                    "spring.cloud.gateway.httpclient.response-timeout=100ms",
                    "spring.cloud.gateway.routes[0].id=account-service",
                    "spring.cloud.gateway.routes[0].predicates[0]=Path=/accounts/**",
                    "spring.cloud.gateway.routes[0].filters[0]=RewritePath=/accounts/(?<path>.*), /$\\{path}",
                    "spring.cloud.gateway.routes[0].filters[1].name=Retry",
                    "spring.cloud.gateway.routes[0].filters[1].args.retries=10",
                    "spring.cloud.gateway.routes[0].filters[1].args.statuses=INTERNAL_SERVER_ERROR",
                    "spring.cloud.gateway.routes[0].filters[1].args.backoff.firstBackoff=50ms",
                    "spring.cloud.gateway.routes[0].filters[1].args.backoff.maxBackoff=500ms",
                    "spring.cloud.gateway.routes[0].filters[1].args.backoff.factor=2",
                    "spring.cloud.gateway.routes[0].filters[1].args.backoff.basedOnPreviousValue=true"
                })
@Testcontainers
@DirtiesContext
@AutoConfigureTestRestTemplate
public class GatewayRetryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRetryTest.class);

    @Container
    static MockServerContainer mockServer = new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

    @Autowired
    TestRestTemplate template;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + mockServer.getHost() + ":" + mockServer.getMappedPort(1080));
    }

    @BeforeAll
    static void init() {
        MockServerClient client = new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort());
        client.when(HttpRequest.request()
                .withPath("/1"), Times.exactly(3))
                .respond(response()
                        .withStatusCode(500)
                        .withBody("{\"errorCode\":\"5.01\"}")
                        .withHeader("Content-Type", "application/json"));
        client.when(HttpRequest.request()
                .withPath("/1"))
                .respond(response()
                        .withBody("{\"id\":1,\"number\":\"1234567891\"}")
                        .withHeader("Content-Type", "application/json"));
        client.when(HttpRequest.request()
                .withPath("/2"))
                .respond(response()
                        .withBody("{\"id\":2,\"number\":\"1234567891\"}")
                        .withDelay(TimeUnit.MILLISECONDS, 200)
                        .withHeader("Content-Type", "application/json"));
    }

    @Test
    public void testAccountService() {
        LOGGER.info("Sending /1...");
        ResponseEntity<Account> r = template.exchange("/accounts/{id}", HttpMethod.GET, null, Account.class, 1);
        LOGGER.info("Received: status->{}, payload->{}", r.getStatusCode().value(), r.getBody());
        assertTrue(r.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void testAccountServiceFail() {
        LOGGER.info("Sending /2...");
        ResponseEntity<Account> r = template.exchange("/accounts/{id}", HttpMethod.GET, null, Account.class, 2);
        LOGGER.info("Received: status->{}, payload->{}", r.getStatusCode().value(), r.getBody());
        assertTrue(r.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(504)));
    }

}
