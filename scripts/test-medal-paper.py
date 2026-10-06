#!/usr/bin/env python3
"""隔離Paperで、メダルの判定・両替・記録・作業台の禁止を検証する。

server-data/ に本番と同じPaperのJAR（paper-26.2-129.jar）と、Vault.jar・EssentialsX-2.22.0.jar を置いておく。
先に `mvn -B package` を実行しておく（target/test-classes を使う）。
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import threading
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server-data"
WORK = ROOT / "target/medal-paper-smoke"
JAVA = os.environ.get("JAVA_BIN", "/opt/homebrew/opt/openjdk/bin/java")


def main():
    if WORK.exists():
        shutil.rmtree(WORK)
    plugins = WORK / "plugins"
    plugins.mkdir(parents=True)
    for source, target in (
        (SOURCE / "paper-26.2-129.jar", WORK / "paper.jar"),
        (SOURCE / "plugins/Vault.jar", plugins / "Vault.jar"),
        (SOURCE / "plugins/EssentialsX-2.22.0.jar", plugins / "EssentialsX.jar"),
        (ROOT / "target/spamedal-1.0.0.jar", plugins / "SpaMedal.jar"),
    ):
        shutil.copy2(source, target)
    with zipfile.ZipFile(plugins / "MedalProbe.jar", "w") as jar:
        jar.writestr("plugin.yml", "name: MedalProbe\nversion: 1\n"
                     "main: dev.spa.spamedal.MedalProbe\n"
                     "api-version: '1.21'\ndepend: [SpaMedal, Vault, Essentials]\n")
        for source in (ROOT / "target/test-classes/dev/spa/spamedal").glob("MedalProbe*.class"):
            jar.write(source, "dev/spa/spamedal/" + source.name)
    (WORK / "eula.txt").write_text("eula=true\n")
    (WORK / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25586\nonline-mode=false\n"
        "spawn-protection=0\nmax-players=1\nlevel-type=minecraft:flat\n"
    )
    process = subprocess.Popen(
        [JAVA, "-Xms512M", "-Xmx1G", "-jar", "paper.jar", "--nogui"],
        cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    lines = []
    done = threading.Event()

    def read_output():
        for line in process.stdout:
            lines.append(line)
            if "MEDAL_PROBE_PASS" in line or "MEDAL_PROBE_FAIL" in line:
                done.set()

    threading.Thread(target=read_output, daemon=True).start()
    try:
        if not done.wait(240):
            raise RuntimeError("Paperの検証が240秒以内に終わりませんでした")
    finally:
        try:
            process.wait(60)
        except subprocess.TimeoutExpired:
            process.kill()
    output = "".join(lines)
    for line in lines:
        if "ok: " in line or "SpaMedal" in line or "MEDAL_PROBE" in line or "Exception" in line or "Error" in line:
            print(line.rstrip())
    if "MEDAL_PROBE_PASS" not in output:
        sys.exit(1)
    log = WORK / "plugins/SpaMedal/medals.log"
    print("medals.log:", log.read_text().count("\n"), "件")


if __name__ == "__main__":
    main()
