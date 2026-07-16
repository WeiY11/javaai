import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

from tools.threegpp_kb.__main__ import build_parser, main
from tools.threegpp_kb.core import Manifest


class CliTest(unittest.TestCase):
    def test_build_defaults_to_complete_archive(self):
        args = build_parser().parse_args(["build"])
        self.assertEqual("build", args.command)
        self.assertEqual([], args.series)
        self.assertIsNone(args.max_files)
        self.assertEqual(4, args.workers)

    def test_status_prints_machine_readable_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            manifest.close()
            output = StringIO()
            with redirect_stdout(output):
                exit_code = main(["--root", directory, "status"])
            self.assertEqual(0, exit_code)
            self.assertIn("discovered=0", output.getvalue())
            self.assertIn("indexed=0", output.getvalue())


if __name__ == "__main__":
    unittest.main()

