package io.sentry.samples.spring;

import io.sentry.core.IHub;
import io.sentry.spring.boot.SentrySecurityFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

  private final @NotNull IHub hub;

  public SecurityConfiguration(final @NotNull IHub hub) {
    this.hub = hub;
  }

  @Override
  protected void configure(final @NotNull HttpSecurity http) throws Exception {
    // register SentrySecurityFilter to attach user information to SentryEvents
    http.addFilterAfter(new SentrySecurityFilter(hub), AnonymousAuthenticationFilter.class)
        .csrf()
        .disable()
        .authorizeRequests()
        .anyRequest()
        .authenticated()
        .and()
        .httpBasic();
  }

  @Bean
  @Override
  public @NotNull UserDetailsService userDetailsService() {
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
