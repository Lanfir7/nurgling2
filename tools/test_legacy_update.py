"""Black-box integration coverage for the legacy nurgling launcher.

Run with: python tools/test_legacy_update.py
"""

from __future__ import annotations

import functools
import hashlib
import http.server
import shutil
import subprocess
import tempfile
import threading
import unittest
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "etc" / "nurgling_launcher.jar"
JAVA = shutil.which("java")
TIMEOUT_SECONDS = 20


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class CountingHttpServer:
    def __init__(self, directory: Path):
        self.requests: Counter[str] = Counter()

        requests = self.requests

        class Handler(http.server.SimpleHTTPRequestHandler):
            def do_GET(self):  # noqa: N802 - required by BaseHTTPRequestHandler
                requests[urlsplit(self.path).path] += 1
                super().do_GET()

            def log_message(self, format: str, *args: object) -> None:
                pass

        handler = functools.partial(Handler, directory=str(directory))
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    @property
    def base_url(self) -> str:
        host, port = self.server.server_address
        return f"http://{host}:{port}/"

    def __enter__(self) -> "CountingHttpServer":
        self.thread.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=TIMEOUT_SECONDS)


@unittest.skipUnless(LAUNCHER.is_file(), "etc/nurgling_launcher.jar is required")
@unittest.skipUnless(JAVA, "java is required")
class LegacyUpdateIntegrationTest(unittest.TestCase):
    version = "99.0.0"
    files = {
        "hafen.jar": b"new dummy hafen jar\n",
        "run_updater.bat": b"@echo off\r\necho migrated stable\r\n",
        "run_updater_latest.bat": b"@echo off\r\necho migrated latest\r\n",
    }

    def make_feed(self, root: Path) -> Path:
        feed = root / "feed"
        feed.mkdir()
        lines = [self.version]
        for name, content in self.files.items():
            (feed / name).write_bytes(content)
            lines.append(f"{name}={sha256(content)}")
        # The launcher is deliberately absent: its hasher excludes this file and
        # replacing a running JAR is not a supported update path.
        (feed / "ver").write_text("\n".join(lines) + "\n", encoding="utf-8")
        return feed

    def run_updater(self, client: Path, base_url: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [JAVA, "-jar", str(LAUNCHER), "update", base_url, "-version"],
            cwd=client,
            capture_output=True,
            text=True,
            timeout=TIMEOUT_SECONDS,
            check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )

    def assert_update_succeeded(self, result: subprocess.CompletedProcess[str]) -> None:
        self.assertEqual(
            0,
            result.returncode,
            f"launcher failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}",
        )

    def test_old_client_downloads_payload_and_skips_second_identical_version(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            feed = self.make_feed(root)
            client = root / "client"
            client.mkdir()
            (client / "ver").write_text("98.0.0\nhafen.jar=old\n", encoding="utf-8")
            (client / "hafen.jar").write_bytes(b"old dummy hafen jar\n")
            (client / "run_updater.bat").write_bytes(b"old bat\n")
            (client / "settings.json").write_text('{"keep": true}\n', encoding="utf-8")
            sentinel = b"do not replace the running launcher\n"
            (client / "nurgling_launcher.jar").write_bytes(sentinel)

            with CountingHttpServer(feed) as server:
                first = self.run_updater(client, server.base_url)
                self.assert_update_succeeded(first)
                downloaded = Counter(server.requests)

                second = self.run_updater(client, server.base_url)
                self.assert_update_succeeded(second)

            self.assertEqual(self.version, (client / "ver").read_text(encoding="utf-8").splitlines()[0])
            for name, content in self.files.items():
                self.assertEqual(content, (client / name).read_bytes(), name)
                self.assertEqual(1, downloaded[f"/{name}"], name)
                self.assertEqual(1, server.requests[f"/{name}"], name)
            self.assertEqual('{"keep": true}\n', (client / "settings.json").read_text(encoding="utf-8"))
            self.assertEqual(sentinel, (client / "nurgling_launcher.jar").read_bytes())
            self.assertEqual(2, server.requests["/ver"])

    def test_fresh_client_without_ver_downloads_complete_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            feed = self.make_feed(root)
            client = root / "fresh-client"
            client.mkdir()

            with CountingHttpServer(feed) as server:
                result = self.run_updater(client, server.base_url)
                self.assert_update_succeeded(result)

            self.assertEqual(self.version, (client / "ver").read_text(encoding="utf-8").splitlines()[0])
            for name, content in self.files.items():
                self.assertEqual(content, (client / name).read_bytes(), name)
                self.assertEqual(1, server.requests[f"/{name}"], name)
            self.assertFalse((client / "nurgling_launcher.jar").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
