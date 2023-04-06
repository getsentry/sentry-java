package io.sentry.samples.spring.boot

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfiguration {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf().disable().authorizeHttpRequests().anyRequest().authenticated().and().httpBasic()
        return http.build()
    }

    @Bean
    fun userDetailsService(): InMemoryUserDetailsManager {
        val encoder: PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

        val user = User.builder()
            .passwordEncoder { encoder.encode(it) }
            .username("user")
            .password("password")
            .roles("USER")
            .build()

        return InMemoryUserDetailsManager(user)
    }
}
