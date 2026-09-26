#!/usr/bin/env python3
"""Count saved chunk records in an Anvil world's overworld region files."""

import json
import struct
import sys
from pathlib import Path


def count_chunks(world: Path) -> dict[str, int]:
    region_dir = world / "region"
    files = sorted(region_dir.glob("*.mca"))
    chunks = 0
    for path in files:
        with path.open("rb") as region:
            header = region.read(4096)
        if len(header) < 4096:
            raise ValueError(f"Invalid Anvil region header: {path}")
        chunks += sum(
            1
            for index in range(1024)
            if struct.unpack_from(">I", header, index * 4)[0] != 0
        )
    return {"region_files": len(files), "saved_overworld_chunks": chunks}


if len(sys.argv) != 2:
    raise SystemExit("Usage: count-minecraft-chunks.py <world-directory>")

print(json.dumps(count_chunks(Path(sys.argv[1]))))
