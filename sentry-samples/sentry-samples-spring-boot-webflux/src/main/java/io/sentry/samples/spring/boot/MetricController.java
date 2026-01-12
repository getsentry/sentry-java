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
    Sentry.metrics().count("countMetric");
    return "count metric increased";
  }

  @GetMapping("gauge/{count}")
  String gauge(@PathVariable Long count) {
    Sentry.metrics().gauge("memory.free", count.doubleValue(), MeasurementUnit.Information.BYTE);
    return "gauge metric tracked";
  }

  @GetMapping("distribution/{count}")
  String distribution(@PathVariable Long count) {
    Sentry.metrics()
        .distribution(
            "distributionMetric", count.doubleValue(), MeasurementUnit.Duration.MILLISECOND);
    return "distribution metric tracked";
  }
}
