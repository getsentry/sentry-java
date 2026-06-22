#!/usr/bin/env python3

import io
import json
import tempfile
import unittest
import unittest.mock
from pathlib import Path

from detect_sdk_name_changes import (
    SDK_MAP_SHEET_URL,
    find_sdk_name_changes,
    main,
    parse_sdk_constants,
)


class DetectSdkNameChangesTest(unittest.TestCase):
    BASE_CONFIG = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
    }
}
"""

    HEAD_WITH_NEW_CONSTANT = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val SENTRY_FOO_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.foo"
    }
}
"""

    HEAD_WITH_NON_SENTRY_PREFIX = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val LEGACY_SDK_NAME = "sentry-java"
    }
}
"""

    HEAD_WITH_CHANGED_VALUE = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android-renamed"
    }
}
"""

    HEAD_WITH_REMOVED_CONSTANT = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
    }
}
"""

    HEAD_WITH_INVALID_RHS = """
object Config {
    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_FOO_SDK_NAME = buildSdkName("foo")
    }
}
"""

    def test_no_changes(self) -> None:
        base = parse_sdk_constants(self.BASE_CONFIG)
        head = parse_sdk_constants(self.BASE_CONFIG)
        self.assertEqual(find_sdk_name_changes(base, head), ([], []))

    def test_new_interpolated_constant(self) -> None:
        base = parse_sdk_constants(self.BASE_CONFIG)
        head = parse_sdk_constants(self.HEAD_WITH_NEW_CONSTANT)
        self.assertEqual(find_sdk_name_changes(base, head), (["sentry.java.foo"], []))

    def test_new_constant_without_sentry_prefix(self) -> None:
        base = parse_sdk_constants(self.BASE_CONFIG)
        head = parse_sdk_constants(self.HEAD_WITH_NON_SENTRY_PREFIX)
        self.assertEqual(find_sdk_name_changes(base, head), (["sentry-java"], []))

    def test_changed_value_detects_add_and_remove(self) -> None:
        base = parse_sdk_constants(self.BASE_CONFIG)
        head = parse_sdk_constants(self.HEAD_WITH_CHANGED_VALUE)
        self.assertEqual(
            find_sdk_name_changes(base, head),
            (["sentry.java.android-renamed"], ["sentry.java.android"]),
        )

    def test_removed_constant(self) -> None:
        base = parse_sdk_constants(self.BASE_CONFIG)
        head = parse_sdk_constants(self.HEAD_WITH_REMOVED_CONSTANT)
        self.assertEqual(find_sdk_name_changes(base, head), ([], ["sentry.java.android"]))

    def test_parse_failure_returns_error(self) -> None:
        with self.assertRaises(ValueError):
            parse_sdk_constants(self.HEAD_WITH_INVALID_RHS)

    def test_cli_reports_parse_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            base_file = temp_path / "base.kt"
            head_file = temp_path / "head.kt"
            base_file.write_text(self.BASE_CONFIG, encoding="utf-8")
            head_file.write_text(self.HEAD_WITH_INVALID_RHS, encoding="utf-8")

            stderr = io.StringIO()
            with unittest.mock.patch("sys.stderr", stderr):
                exit_code = main(
                    ["--base-file", str(base_file), "--head-file", str(head_file)]
                )

            self.assertEqual(exit_code, 1)
            self.assertIn("Cannot parse SDK name assignment", stderr.getvalue())

    def test_cli_prints_json_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            base_file = temp_path / "base.kt"
            head_file = temp_path / "head.kt"
            base_file.write_text(self.BASE_CONFIG, encoding="utf-8")
            head_file.write_text(self.HEAD_WITH_NEW_CONSTANT, encoding="utf-8")

            buffer = io.StringIO()
            with unittest.mock.patch("sys.stdout", buffer):
                exit_code = main(
                    ["--base-file", str(base_file), "--head-file", str(head_file)]
                )

            self.assertEqual(exit_code, 0)
            self.assertEqual(
                json.loads(buffer.getvalue()),
                {
                    "added": ["sentry.java.foo"],
                    "removed": [],
                    "sdk_map_sheet_url": SDK_MAP_SHEET_URL,
                },
            )


if __name__ == "__main__":
    unittest.main()
