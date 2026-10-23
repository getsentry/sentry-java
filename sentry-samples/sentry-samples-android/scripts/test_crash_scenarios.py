import importlib.util
import io
import queue
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "crash_scenarios", Path(__file__).with_name("crash-scenarios.py")
)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CrashScenariosTest(unittest.TestCase):
    def parse(self, *entries):
        lines = queue.Queue()
        for entry in entries:
            lines.put(entry + "\n")
        lines.put(None)
        return runner.wait_for_death(
            lines, io.StringIO(), "io.sentry.samples.android", "baseline", "test-run", 1
        )

    def test_uses_fault_marker_and_matching_process_death(self):
        pid, status, elapsed = self.parse(
            "         100.000 123 123 W SentryCrashScenario: status=ready scenario=baseline run_id=test-run",
            "100.100 123 123 W SentryCrashScenario: status=fault scenario=baseline run_id=other-run",
            "         100.200 123 123 W SentryCrashScenario: status=fault scenario=baseline run_id=test-run",
            "100.300 900 901 I ActivityManager: Process io.sentry.samples.android (pid 456) has died: fg TOP",
            "100.400 900 901 I ActivityManager: Process io.sentry.samples.android.debug (pid 123) has died: fg TOP",
            "         100.450 900 901 I ActivityManager: Process io.sentry.samples.android (pid 123) has died: fg TOP",
        )
        self.assertEqual((pid, status), (123, "died"))
        self.assertAlmostEqual(elapsed, 250)

    def test_does_not_time_a_death_before_the_fault(self):
        self.assertEqual(
            self.parse(
                "100.000 123 123 W SentryCrashScenario: status=ready scenario=baseline run_id=test-run",
                "100.450 900 901 I ActivityManager: Process io.sentry.samples.android (pid 123) has died: fg TOP",
            ),
            (123, "died_before_fault", None),
        )

    def test_reports_setup_failure_without_a_timing(self):
        self.assertEqual(
            self.parse(
                "100.000 123 123 E SentryCrashScenario: status=error scenario=baseline run_id=test-run: unavailable",
            ),
            (123, "setup_error", None),
        )

    def test_reports_logcat_disconnect_without_a_timing(self):
        self.assertEqual(
            self.parse(
                "100.000 123 123 W SentryCrashScenario: status=fault scenario=baseline run_id=test-run",
            ),
            (123, "logcat_stopped", None),
        )

    def test_reports_timeout_without_a_fault(self):
        self.assertEqual(
            runner.wait_for_death(
                queue.Queue(),
                io.StringIO(),
                "io.sentry.samples.android",
                "baseline",
                "test-run",
                0,
            ),
            (None, "fault_timeout", None),
        )

    def test_reports_timeout_after_the_fault(self):
        lines = queue.Queue()
        lines.put(
            "100.000 123 123 W SentryCrashScenario: status=fault scenario=baseline run_id=test-run\n"
        )
        self.assertEqual(
            runner.wait_for_death(
                lines,
                io.StringIO(),
                "io.sentry.samples.android",
                "baseline",
                "test-run",
                1,
            ),
            (123, "death_timeout", None),
        )


if __name__ == "__main__":
    unittest.main()
