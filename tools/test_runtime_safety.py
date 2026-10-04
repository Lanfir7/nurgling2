import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from xml.sax.saxutils import escape
import xml.etree.ElementTree as ET
import zipfile

from check_client_runtime import uses_runtime


ROOT = Path(__file__).resolve().parents[1]


class RuntimeReferencesTest(unittest.TestCase):
    def test_quoted_path_classpath_and_directory_loads_are_protected(self):
        directory = ROOT / "bin"
        for command in (f'java -jar "{directory}/hafen.jar"',
                        f'java -cp "{directory};elsewhere" haven.MainFrame',
                        f'java -cp "{directory}" haven.MainFrame',
                        'java -jar bin/hafen.jar',
                        r'java -jar .\bin\hafen.jar',
                        r'java -cp ".\bin\*" haven.MainFrame',
                        'java -jar temporary/../bin/hafen.jar'):
            self.assertTrue(uses_runtime(command, directory, ROOT), command)

    def test_separate_snapshot_and_other_project_are_not_blocked(self):
        self.assertFalse(uses_runtime(f'java -jar "{ROOT}/.client-runtimes/client-123/hafen.jar"', ROOT / "bin"))
        self.assertFalse(uses_runtime('java -jar bin/hafen.jar', ROOT / "bin", ROOT / "other-project"))
        self.assertFalse(uses_runtime(f'java -jar "{ROOT}/bin-old/hafen.jar"', ROOT / "bin"))


@unittest.skipUnless(shutil.which("ant") and shutil.which("javac"), "requires Ant and a JDK")
class RuntimeSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="client-runtime-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        # PATH may contain a Java shim that spawns another process. Own the
        # actual JVM, so stopping this fixture cannot leave a child behind.
        settings = subprocess.run(["java", "-XshowSettings:properties", "-version"],
                                  check=True, capture_output=True, text=True)
        java_home = Path(re.search(r"java.home = (.+)", settings.stderr).group(1).strip())
        self.java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
        for name in ("bin", "build", "etc", "tools", "lib/ext/jogl", "lib/ext/lwjgl",
                     "lib/ext/lwjgl-vk", "lib/ext/steamworks", "lib/ext"):
            (self.root / name).mkdir(parents=True, exist_ok=True)
        script = ROOT / "tools/check_client_runtime.py"
        if script.exists():
            shutil.copyfile(script, self.root / "tools" / script.name)
        # Override only compilation/download tasks. Exercise the actual runtime,
        # bin and clean target bodies and dependency order from the project.
        overrides = ("jar", "opt/panama", "extlib/jogl-arm", "extlib/jogl",
                     "extlib/lwjgl-gl", "extlib/steamworks", "res-jar")
        project = '<project default="runtime" basedir="."><import file="{}"/>'.format(
            escape(str(ROOT / "build.xml")))
        project += ''.join('<target name="{}"/>'.format(name) for name in overrides)
        (self.root / "build.xml").write_text(project + '</project>', encoding="utf-8")
        source = self.root / "Hold.java"
        source.write_text('public class Hold { public static void main(String[] args) throws Exception {'
                          ' System.out.println("READY"); if(System.getenv("CLIENT_RUNTIME_TEST_EXIT") == null)'
                          ' Thread.sleep(60000); } }', encoding="utf-8")
        subprocess.run(["javac", str(source)], check=True, capture_output=True)
        with zipfile.ZipFile(self.root / "bin/hafen.jar", "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: Hold\n\n")
            jar.write(self.root / "Hold.class", "Hold.class")
        shutil.copyfile(self.root / "bin/hafen.jar", self.root / "build/hafen.jar")
        for name in ("builtin-res.jar", "hafen-res.jar"):
            (self.root / "bin" / name).write_bytes(b"original resource")
            (self.root / "lib/ext" / name).write_bytes(b"fresh resource")
        (self.root / "build/nurgling-res.jar").write_bytes(b"built resource")
        for name in ("json-java.jar", "postgresql-42.7.5.jar", "sqlite-jdbc-3.49.1.0.jar"):
            (self.root / "etc" / name).write_bytes(b"original dependency")
        (self.root / "etc/ansgar-config.properties").write_bytes(b"haven.server=test-server\n")
        (self.root / "lib/ext/jogl/jogl-all.jar").write_bytes(b"original jogl")

    def ant(self, target, env=None):
        # On Windows Ant is a .bat; use its installed launcher, not a shell-built command.
        return subprocess.run([shutil.which("ant"), "-f", str(self.root / "build.xml"), target],
                              cwd=self.root, capture_output=True, text=True, timeout=40, env=env)

    def start_old_client(self):
        process = subprocess.Popen([str(self.java), "-jar", str(self.root / "bin/hafen.jar")],
                                   cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        def stop():
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
            process.stdout.close()
            process.stderr.close()
        self.addCleanup(stop)
        self.assertEqual("READY", process.stdout.readline().strip())
        return process

    def test_bin_and_clean_refuse_to_modify_a_live_clients_files(self):
        process = self.start_old_client()
        before = (self.root / "bin/hafen.jar").read_bytes()
        for target in ("bin", "clean"):
            result = self.ant(target)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("running client", result.stdout + result.stderr)
            self.assertIn(str(process.pid), result.stdout + result.stderr)
            self.assertEqual(before, (self.root / "bin/hafen.jar").read_bytes())
            self.assertTrue((self.root / "build/hafen.jar").exists())
        process.terminate()
        process.wait(timeout=10)
        result = self.ant("bin")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_runtime_is_complete_unique_and_survives_rebuild_and_clean(self):
        self.start_old_client()
        before = (self.root / "bin/hafen.jar").read_bytes()
        result = self.ant("runtime")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        snapshots = list((self.root / ".client-runtimes").iterdir())
        self.assertEqual(1, len(snapshots))
        runtime = snapshots[0]
        self.assertEqual(before, (runtime / "hafen.jar").read_bytes())
        self.assertEqual(b"built resource", (runtime / "nurgling-res.jar").read_bytes())
        self.assertEqual(b"fresh resource", (runtime / "builtin-res.jar").read_bytes())
        self.assertEqual(b"fresh resource", (runtime / "hafen-res.jar").read_bytes())
        self.assertEqual(b"original dependency", (runtime / "json-java.jar").read_bytes())
        self.assertEqual(b"haven.server=test-server\n", (runtime / "haven-config.properties").read_bytes())
        self.assertEqual(before, (self.root / "bin/hafen.jar").read_bytes())
        (self.root / "build/hafen.jar").write_bytes(b"another build")
        result = self.ant("runtime")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(2, len(list((self.root / ".client-runtimes").iterdir())))
        self.assertEqual(before, (runtime / "hafen.jar").read_bytes())
        # Remove only the disposable fixture's build/libs; a running snapshot
        # must not depend on mutable bin, build or lib directories.
        shutil.rmtree(self.root / "build")
        shutil.rmtree(self.root / "lib")
        self.assertEqual(b"original jogl", (runtime / "jogl-all.jar").read_bytes())
        self.assertEqual(before, (runtime / "hafen.jar").read_bytes())

    def test_run_removes_only_its_copy_after_the_jvm_exits(self):
        self.start_old_client()
        prepared = self.ant("runtime")
        self.assertEqual(0, prepared.returncode, prepared.stdout + prepared.stderr)
        original = list((self.root / ".client-runtimes").iterdir())
        result = self.ant("run", env=dict(os.environ, CLIENT_RUNTIME_TEST_EXIT="1"))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("READY", result.stdout)
        self.assertEqual(original, list((self.root / ".client-runtimes").iterdir()))

    def test_api_rollback_recompiles_callers_and_refuses_an_inconsistent_jar(self):
        source = self.root / "src"
        classes = self.root / "build/classes"
        source.mkdir()
        classes.mkdir()
        (self.root / "build/classes-lib").mkdir()
        caller = source / "Caller.java"
        callee = source / "Callee.java"
        caller.write_text('public class Caller { public void run() { new Callee().retainOverlay(); } }')
        callee.write_text('public class Callee { public void retainOverlay() {} }')
        subprocess.run(["javac", "-d", str(classes), str(caller), str(callee)],
                       check=True, capture_output=True)
        # Only the callee source changes, exactly the partial API rollback
        # which otherwise leaves incremental caller bytecode linked to an absent method.
        callee.write_text('public class Callee {}')
        project = '<project basedir="."><import file="{}"/>'.format(escape(str(ROOT / "build.xml")))
        for target in ("buildinfo", "lib-classes", "package-client-news", "extlib/jogl",
                       "extlib/lwjgl-gl", "extlib/lwjgl-vk", "extlib/steamworks", "resources"):
            project += '<target name="{}"/>'.format(target)
        dependencies = ET.parse(ROOT / "build.xml").find("target[@name='hafen-client']").attrib["depends"]
        project += ('<target name="hafen-client" depends="{}"><javac srcdir="src" '
                    'destdir="build/classes" includeantruntime="false"/></target></project>').format(dependencies)
        (self.root / "build.xml").write_text(project, encoding="utf-8")
        before = (self.root / "build/hafen.jar").read_bytes()
        result = self.ant("jar")
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("retainOverlay", result.stdout + result.stderr)
        self.assertEqual(before, (self.root / "build/hafen.jar").read_bytes())


if __name__ == "__main__":
    unittest.main()
