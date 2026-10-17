from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "plan_ysuite_debug.py"
SPEC = importlib.util.spec_from_file_location("plan_ysuite_debug", SCRIPT)
assert SPEC and SPEC.loader
plan = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(plan)


class PlanYSuiteDebugTests(unittest.TestCase):
    def test_only_changed_yfloat_module_is_tested(self):
        self.assertEqual(
            (":yfloat-feature:testDebugUnitTest",),
            plan.choose_tests([
                "apps/YFloat/feature/src/main/java/com/yagay/YFloat/GoogleHookManualScan.java"
            ]),
        )

    def test_unrelated_feature_does_not_trigger_five_tests(self):
        self.assertEqual((), plan.choose_tests([
            "apps/YFiles/feature/src/main/java/example/Viewer.java"
        ]))

    def test_shared_api_change_tests_all(self):
        self.assertEqual(plan.ALL_TESTS, plan.choose_tests([
            "libs/yui/src/main/java/com/yagay/yui/YView.java"
        ]))

    def test_core_build_settings_change_tests_all(self):
        self.assertEqual(plan.ALL_TESTS, plan.choose_tests([
            "gradle.properties"
        ]))

    def test_unknown_git_diff_runs_every_test(self):
        self.assertEqual(plan.ALL_TESTS, plan.choose_tests(None))

    def test_readme_does_not_trigger_unit_tests(self):
        self.assertEqual((), plan.choose_tests(["apps/YFloat/docs/README.md"]))


if __name__ == "__main__":
    unittest.main()
