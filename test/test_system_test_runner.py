import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent / "system-test-runner.py"
SPEC = importlib.util.spec_from_file_location("system_test_runner", MODULE_PATH)
SYSTEM_TEST_RUNNER = importlib.util.module_from_spec(SPEC)
sys.modules.setdefault("requests", SimpleNamespace(head=None))
sys.modules[SPEC.name] = SYSTEM_TEST_RUNNER
assert SPEC.loader is not None
SPEC.loader.exec_module(SYSTEM_TEST_RUNNER)


class SystemTestRunnerTests(unittest.TestCase):
    def create_runner(self):
        with patch.object(SYSTEM_TEST_RUNNER.SystemTestRunner, "read_pid_file", return_value=None):
            return SYSTEM_TEST_RUNNER.SystemTestRunner()

    def test_wait_for_spring_requires_actuator_health(self):
        runner = self.create_runner()

        def fake_head(url, auth, timeout):
            self.assertEqual(url, "http://localhost:8080/actuator/health")
            return SimpleNamespace(status_code=503)

        with patch.object(SYSTEM_TEST_RUNNER.requests, "head", side_effect=fake_head), patch.object(
            SYSTEM_TEST_RUNNER.time, "sleep"
        ):
            self.assertFalse(runner.wait_for_spring(max_attempts=1))

    def test_get_spring_status_only_reports_http_ready_for_actuator_health(self):
        runner = self.create_runner()
        runner.spring_server.pid = 123

        def fake_head(url, auth, timeout):
            self.assertEqual(url, "http://localhost:8080/actuator/health")
            return SimpleNamespace(status_code=404)

        with patch.object(SYSTEM_TEST_RUNNER.requests, "head", side_effect=fake_head), patch.object(
            runner, "is_process_running", return_value=True
        ):
            status = runner.get_spring_status()

        self.assertTrue(status["process_running"])
        self.assertFalse(status["http_ready"])


if __name__ == "__main__":
    unittest.main()
