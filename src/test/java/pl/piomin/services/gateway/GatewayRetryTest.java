package pl.piomin.services.gateway;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MockServerContainer;
import pl.piomin.services.gateway.model.Account;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class GatewayRetryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRetryTest.class);

    @ClassRule
    public static MockServerContainer mockServer = new MockServerContainer();

    @Autowired
    TestRestTemplate template;

    @BeforeClass
    public static void init() {
        System.setProperty("spring.cloud.gateway.httpclient.response-timeout", "100ms");
        System.setProperty("spring.cloud.gateway.routes[0].id", "account-service");
        System.setProperty("spring.cloud.gateway.routes[0].uri", "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/account/**");
        System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/account/(?<path>.*), /$\\{path}");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "Retry");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.retries", "10");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.statuses", "INTERNAL_SERVER_ERROR");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.firstBackoff", "50ms");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.maxBackoff", "500ms");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.factor", "2");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.basedOnPreviousValue", "true");
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
        ResponseEntity<Account> r = template.exchange("/account/{id}", HttpMethod.GET, null, Account.class, 1);
        LOGGER.info("Received: status->{}, payload->{}", r.getStatusCodeValue(), r.getBody());
        Assert.assertEquals(200, r.getStatusCodeValue());
    }

    @Test
    public void testAccountServiceFail() {
        LOGGER.info("Sending /2...");
        ResponseEntity<Account> r = template.exchange("/account/{id}", HttpMethod.GET, null, Account.class, 2);
        LOGGER.info("Received: status->{}, payload->{}", r.getStatusCodeValue(), r.getBody());
        Assert.assertEquals(504, r.getStatusCodeValue());
    }

}
