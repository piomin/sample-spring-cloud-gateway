package pl.piomin.services.gateway;

import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.dsl.HoverflyDsl;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import pl.piomin.services.gateway.model.Account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers.startsWith;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class GatewayApplicationTest {

	@Autowired
	TestRestTemplate template;

	@ClassRule
	public static HoverflyRule hoverflyRule = HoverflyRule
			.inSimulationMode(SimulationSource.dsl(HoverflyDsl.service("localhost:8091").get(startsWith("/accounts/"))
					.willReturn(success("[{\"id\":\"1\",\"number\":\"1234567890\"}]", "application/json"))))
			.printSimulationData();

	@Test
	public void testAccountService() {
		Account account = template.getForObject("/account/{id}", Account.class, 1);
		Assert.assertNotNull(account);
		Assert.assertEquals(Integer.valueOf(1), account.getId());
		Assert.assertEquals("1234567890", account.getNumber());
	}

}
