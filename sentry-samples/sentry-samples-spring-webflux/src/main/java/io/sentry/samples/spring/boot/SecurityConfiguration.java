package io.sentry.samples.spring.boot;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(final @NotNull ServerHttpSecurity http) {
    return http.csrf()
        .disable()
        .authorizeExchange()
        .anyExchange()
        .authenticated()
        .and()
        .httpBasic()
        .and()
        .build();
  }

  @Bean
  public MapReactiveUserDetailsService userDetailsService() {
    final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    final UserDetails user =
        User.builder()
            .passwordEncoder(encoder::encode)
            .username("user")
            .password("password")
            .roles("USER")
            .build();
    return new MapReactiveUserDetailsService(user);
  }
}
