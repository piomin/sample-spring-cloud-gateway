package pl.piomin.services.gateway;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import pl.piomin.services.gateway.model.Account;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class GatewayApplicationTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(GatewayApplicationTest.class);

	@Autowired
	TestRestTemplate template;

	@Test
	public void testAccountService() {
		Account account = template.getForObject("/account/{id}", Account.class, 1);
		Assert.assertNotNull(account);
		Assert.assertEquals(Integer.valueOf(1), account.getId());
		Assert.assertEquals("1234567890", account.getNumber());
	}

}
