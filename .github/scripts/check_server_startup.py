"""Check that the packaged mod loads in a dedicated Fabric server."""

import queue
import re
import subprocess
import sys
import threading
import time
from pathlib import Path


def main() -> int:
    server_dir = Path(sys.argv[1]).resolve()
    process = subprocess.Popen(
        ["java", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
        cwd=server_dir,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    output = queue.Queue()

    def collect_output() -> None:
        for line in process.stdout:
            print(line, end="", flush=True)
            output.put(line)
        output.put(None)

    threading.Thread(target=collect_output, daemon=True).start()
    deadline = time.monotonic() + 300
    mod_loaded = False
    server_ready = False

    try:
        while time.monotonic() < deadline:
            try:
                line = output.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue

            if line is None:
                break
            mod_loaded |= "Smart Worker initialized" in line
            server_ready |= re.search(r"\bDone \([^)]*\)!", line) is not None
            if mod_loaded and server_ready:
                break

        if not mod_loaded or not server_ready:
            print(
                f"Startup check failed: mod initialized={mod_loaded}, "
                f"server ready={server_ready}, exit code={process.poll()}",
                file=sys.stderr,
            )
            return 1

        print("Smart Worker loaded and the dedicated server is ready.", flush=True)
        process.stdin.write("stop\n")
        process.stdin.flush()
        try:
            return process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            print("Server did not stop promptly; startup check passed.", flush=True)
            return 0
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    sys.exit(main())
