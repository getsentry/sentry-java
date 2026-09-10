package io.sentry.samples.spring.boot;

import io.sentry.MeasurementUnit;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metric/")
public class MetricController {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricController.class);

  @GetMapping("count")
  String count() {
    Sentry.setAttribute("user.type", "admin");
    Sentry.setAttribute("feature.version", 2);
    Sentry.metrics().count("countMetric");
    return "count metric increased";
  }

  @GetMapping("gauge/{value}")
  String gauge(@PathVariable("value") Long value) {
    Sentry.metrics()
        .gauge("memory.free", value.doubleValue(), MeasurementUnit.Information.BYTE.apiName());
    return "gauge metric tracked";
  }

  @GetMapping("distribution/{value}")
  String distribution(@PathVariable("value") Long value) {
    Sentry.metrics()
        .distribution(
            "distributionMetric",
            value.doubleValue(),
            MeasurementUnit.Duration.MILLISECOND.apiName());
    return "distribution metric tracked";
  }
}
