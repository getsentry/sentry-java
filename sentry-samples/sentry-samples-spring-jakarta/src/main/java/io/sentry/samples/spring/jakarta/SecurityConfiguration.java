package io.sentry.samples.spring.jakarta;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  // this API is meant to be consumed by non-browser clients thus the CSRF protection is not needed.
  @SuppressWarnings({"lgtm[java/spring-disabled-csrf-protection]", "removal"})
  @Bean
  public SecurityFilterChain filterChain(final @NotNull HttpSecurity http) throws Exception {
    http.csrf().disable().authorizeHttpRequests().anyRequest().authenticated().and().httpBasic();

    return http.build();
  }

  @Bean
  public @NotNull InMemoryUserDetailsManager userDetailsService() {
    final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    final UserDetails user =
        User.builder()
            .passwordEncoder(encoder::encode)
            .username("user")
            .password("password")
            .roles("USER")
            .build();

    return new InMemoryUserDetailsManager(user);
  }
}
