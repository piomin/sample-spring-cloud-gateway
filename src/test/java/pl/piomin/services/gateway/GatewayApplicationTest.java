package pl.piomin.services.gateway;

import org.junit.*;
import org.junit.runner.RunWith;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MockServerContainer;
import pl.piomin.services.gateway.model.Account;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class GatewayApplicationTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(GatewayApplicationTest.class);

	@ClassRule
	public static MockServerContainer mockServer = new MockServerContainer();

	@Autowired
	TestRestTemplate template;

	@BeforeClass
	public static void init() {
		System.setProperty("spring.cloud.gateway.routes[0].id", "account-service");
		System.setProperty("spring.cloud.gateway.routes[0].uri", "http://192.168.99.100:" + mockServer.getServerPort());
		System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/account/**");
		System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/account/(?<path>.*), /$\\{path}");
		new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort())
				.when(HttpRequest.request()
						.withPath("/1"))
				.respond(response()
						.withBody("{\"id\":1,\"number\":\"1234567890\"}")
						.withHeader("Content-Type", "application/json"));
	}

	@Test
	public void testAccountService() {
		Account account = template.getForObject("/account/{id}", Account.class, 1);
		Assert.assertNotNull(account);
		Assert.assertEquals(Integer.valueOf(1), account.getId());
		Assert.assertEquals("1234567890", account.getNumber());
	}

}
