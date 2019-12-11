package pl.piomin.services.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.services.gateway.model.Account;

@RestController
@RequestMapping("/fallback")
public class GatewayFallback {

    @GetMapping("/account")
    public Account getAccount() {
        Account a = new Account();
        a.setId(2);
        a.setNumber("123456");
        return a;
    }

}
