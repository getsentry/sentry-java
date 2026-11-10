# Temporary helper for robolectric-bench.yml. Not for merge.
# Prints one BENCH line per module: the span from the first test class start to the last end.
import glob
import sys
import xml.etree.ElementTree as ET
from datetime import datetime

variant, phase = sys.argv[1], sys.argv[2]
for module in ["sentry-android-core", "sentry-android-replay"]:
    starts, ends, tests, failures = [], [], 0, 0
    for f in glob.glob(f"{module}/build/test-results/testReleaseUnitTest/*.xml"):
        root = ET.parse(f).getroot()
        start = datetime.fromisoformat(root.get("timestamp")).timestamp()
        starts.append(start)
        ends.append(start + float(root.get("time")))
        tests += int(root.get("tests"))
        failures += int(root.get("failures")) + int(root.get("errors"))
    span = max(ends) - min(starts) if starts else -1
    print(f"BENCH variant={variant} phase={phase} module={module} span={span:.1f}s tests={tests} failures={failures}")
