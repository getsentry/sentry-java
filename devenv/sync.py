from devenv import constants
from devenv.lib import config, proc, uv
import os

def main(context: dict[str, str]) -> int:
    reporoot = context["reporoot"]
    cfg = config.get_repo(reporoot)

    uv.install(
        cfg["uv"]["version"],
        cfg["uv"][constants.SYSTEM_MACHINE],
        cfg["uv"][f"{constants.SYSTEM_MACHINE}_sha256"],
        reporoot,
    )

    # reporoot/.venv is the default venv location
    print(f"syncing .venv ...")
    if not os.path.exists(".venv"):
        proc.run(("uv", "venv", "--seed"))
    proc.run(("uv", "sync", "--frozen", "--quiet"))

    return 0

