package io.sentry.spring7.integrationtest

import io.sentry.Sentry
import io.sentry.metrics.MetricsUnit
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metric/")
open class MetricController {
  @GetMapping("count")
  open fun count(): String {
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.metrics().count("countMetric")
    return "count metric increased"
  }

  @GetMapping("gauge/{value}")
  open fun gauge(@PathVariable("value") value: Long): String {
    Sentry.metrics().gauge("memory.free", value.toDouble(), MetricsUnit.Information.BYTE)
    return "gauge metric tracked"
  }

  @GetMapping("distribution/{value}")
  open fun distribution(@PathVariable("value") value: Long): String {
    Sentry.metrics()
      .distribution("distributionMetric", value.toDouble(), MetricsUnit.Duration.MILLISECOND)
    return "distribution metric tracked"
  }
}
