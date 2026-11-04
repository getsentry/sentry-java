package io.sentry.spring.integrationtest

import io.sentry.Sentry
import io.sentry.spring.EnableSentry
import io.sentry.spring.tracing.SentryTracingFilter
import javax.servlet.Filter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer

// Classes and methods are open because Spring proxies them and this module doesn't apply the
// kotlin-spring plugin.

/** Sets the app up the way a WAR does, for the servlet container to start. */
class AppInitializer : AbstractAnnotationConfigDispatcherServletInitializer() {
  override fun getRootConfigClasses(): Array<Class<*>> = arrayOf(AppConfig::class.java)

  override fun getServletConfigClasses(): Array<Class<*>> = arrayOf(WebConfig::class.java)

  override fun getServletMappings(): Array<String> = arrayOf("/*")

  // Creates Sentry transactions around incoming requests.
  override fun getServletFilters(): Array<Filter> = arrayOf(SentryTracingFilter())
}

// The DSN and the traces path come from system properties, and the other options from
// sentry.properties, all of which Sentry reads as external configuration.
@Configuration @EnableSentry(dsn = "", sendDefaultPii = true) open class AppConfig

// Declares the controllers rather than scanning for them, which would also pick up AppConfig and
// initialize Sentry a second time in the servlet's context.
@Configuration
@EnableWebMvc
open class WebConfig {
  @Bean open fun personController() = PersonController()

  @Bean open fun metricController() = MetricController()
}

open class Person(var firstName: String? = null, var lastName: String? = null)

@RestController
@RequestMapping("/person/")
open class PersonController {
  /** Reports to Sentry through every API an app uses, then fails the request. */
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long): Person {
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.setAttribute("debug.enabled", true)
    Sentry.logger().warn("warn Sentry logging")
    Sentry.logger().error("error Sentry logging")
    Sentry.logger().info("hello %s %s", "there", "world!")
    Sentry.addFeatureFlag("my-feature-flag", true)
    throw IllegalArgumentException("Something went wrong [id=$id]")
  }

  @PostMapping open fun create(@RequestBody person: Person): Person = person
}
