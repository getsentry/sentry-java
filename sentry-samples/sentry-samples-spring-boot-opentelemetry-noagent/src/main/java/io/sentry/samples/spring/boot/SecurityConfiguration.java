package io.sentry.samples.spring.boot;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@SuppressWarnings("deprecation") // WebSecurityConfigurerAdapter has been deprecated
public class SecurityConfiguration
    extends org.springframework.security.config.annotation.web.configuration
        .WebSecurityConfigurerAdapter {

  // this API is meant to be consumed by non-browser clients thus the CSRF protection is not needed.
  @Override
  @SuppressWarnings("lgtm[java/spring-disabled-csrf-protection]")
  protected void configure(final @NotNull HttpSecurity http) throws Exception {
    http.csrf().disable().authorizeRequests().anyRequest().authenticated().and().httpBasic();
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
