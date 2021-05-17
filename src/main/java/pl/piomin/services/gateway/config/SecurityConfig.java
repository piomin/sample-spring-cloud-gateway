package pl.piomin.services.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
//@ConditionalOnProperty("rateLimiter.secure")
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .httpBasic();
        http.csrf().disable();
        return http.build();
//        return http
//                .csrf().disable()
//                .authorizeExchange()
//                .anyExchange().authenticated()
//                .and()
//                .httpBasic()
//                .and()
//                .formLogin().disable()
//                .build();
    }

    @Bean
    public MapReactiveUserDetailsService users() {
        UserDetails user1 = User.builder()
                .username("user1")
                .password("{noop}1234")
                .roles("USER")
                .build();
        UserDetails user2 = User.builder()
                .username("user2")
                .password("{noop}1234")
                .roles("USER")
                .build();
        UserDetails user3 = User.builder()
                .username("user3")
                .password("{noop}1234")
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(user1, user2, user3);
    }
}