"""Launch the built diagnostic client without replacing the installed client JAR."""
import argparse
from datetime import datetime
import os
from pathlib import Path
import shutil
import subprocess
from uuid import uuid4
import zipfile


def prepare_runtime(jar: Path, config: Path, directory: Path) -> Path:
    """Keep a running JVM's JAR independent of subsequent Ant rebuilds."""
    directory.mkdir(parents=True, exist_ok=False)
    runtime_jar = directory / "hafen.jar"
    shutil.copyfile(jar, runtime_jar)
    shutil.copyfile(config, directory / "haven-config.properties")
    return runtime_jar


def backup_player_settings(data_directory: Path, directory: Path) -> None:
    """Preserve the pre-launch settings independently of the client's rotating backup."""
    directory.mkdir(parents=True, exist_ok=False)
    for name in ("nconfig.nurgling.json", "nconfig.nurgling.json.bak",
                 "hotkey-presets.json", "hotkey-presets.json.bak"):
        source = data_directory / name
        if source.is_file():
            shutil.copy2(source, directory / name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    jar = root / "build" / "hafen.jar"
    if not jar.is_file():
        parser.error("Build the client first: ant jar")
    config = root / "bin" / "haven-config.properties"
    if not config.is_file():
        parser.error("bin/haven-config.properties is missing; prepare the normal runtime first.")
    # Config reads this file beside the main JAR, not from the working directory/classpath.
    # Resolve only its declared libraries; including the installed client a second time
    # duplicates resource-layer discovery.
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n", "\n").replace("\n ", "")
    dependencies = next(line.partition(": ")[2].split() for line in manifest.splitlines()
                        if line.startswith("Class-Path: "))
    logs = root / "build" / "performance"
    run = datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid4().hex[:8]
    runtime_dir = logs / (run + "-runtime")
    runtime_jar = runtime_dir / "hafen.jar"
    class_path = [runtime_jar, root / "bin"] + [root / "bin" / name for name in dependencies]
    jcmd = shutil.which("jcmd")
    if not jcmd:
        parser.error("Add a JDK with jcmd to PATH.")
    java = Path(jcmd).resolve().with_name("java.exe" if os.name == "nt" else "java")
    if not java.is_file():
        parser.error("java was not found beside jcmd.")
    command = [str(java), "-Xms512m", "-Xmx4g", "-Xss2m",
               "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+UseZGC", "-XX:+ZGenerational",
               "-XX:SoftRefLRUPolicyMSPerMB=50", "-XX:+UseStringDeduplication",
               "-Dsun.java2d.uiScale.enabled=false", "-Djava.net.preferIPv6Addresses=system",
               "-Dhaven.perf=true", "--add-exports=java.base/java.lang=ALL-UNNAMED",
               "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
               "--enable-native-access=ALL-UNNAMED", "-cp",
               os.pathsep.join(map(str, class_path)),
               "haven.MainFrame"]
    if args.dry_run:
        print("Working directory:", root)
        print(subprocess.list2cmdline(command))
        return
    prepare_runtime(jar, config, runtime_dir)
    appdata = os.environ.get("APPDATA")
    player_data = Path(appdata) / "Haven and Hearth" if appdata else Path.home() / ".haven"
    backup_player_settings(player_data, runtime_dir / "settings-backup")
    with (logs / (run + "-stdout.log")).open("wb") as out, (logs / (run + "-stderr.log")).open("wb") as err:
        process = subprocess.Popen(command, cwd=root, stdout=out, stderr=err,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    print("Diagnostic client PID:", process.pid)
    print("Runtime snapshot:", runtime_jar)
    print("Frame capture is enabled. After login: perf start; after the scenario: perf stop, perf export.")
    print("Logs:", logs / (run + "-stderr.log"))


if __name__ == "__main__":
    main()
