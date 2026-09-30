"""Read saved Anvil data to audit benchmark actors and inventory contents without changing the world."""
import argparse
import collections
import gzip
import importlib.util
import json
import struct
import zlib
from pathlib import Path

spec = importlib.util.spec_from_file_location("umce_nbt", Path(__file__).with_name("count-minecraft-entities.py"))
nbt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(nbt)


def roots(directory):
    for region in sorted(directory.glob("r.*.mca")):
        raw = region.read_bytes()
        if len(raw) < 8192:
            raise ValueError(f"Invalid region header: {region}")
        for slot in range(1024):
            sector = int.from_bytes(raw[slot * 4:slot * 4 + 3], "big")
            if not sector:
                continue
            at = sector * 4096
            size = struct.unpack_from(">I", raw, at)[0]
            compression = raw[at + 4]
            payload = raw[at + 5:at + 4 + size]
            if compression == 1:
                payload = gzip.decompress(payload)
            elif compression == 2:
                payload = zlib.decompress(payload)
            elif compression != 3:
                raise ValueError(f"Unsupported compression {compression}: {region}")
            if not payload or payload[0] != 10:
                raise ValueError(f"Missing compound root: {region}")
            _, offset = nbt.read_string(payload, 1)
            root, _ = nbt.read_payload(payload, offset, 10)
            yield root


def audit(world):
    entities = collections.Counter()
    no_ai = collections.Counter()
    nonpositive_health = collections.Counter()
    for root in roots(world / "entities"):
        for entity in root.get("Entities", []):
            kind = entity.get("id", "unknown")
            entities[kind] += 1
            if entity.get("NoAI", 0):
                no_ai[kind] += 1
            if "Health" in entity and entity["Health"] <= 0:
                nonpositive_health[kind] += 1
    block_entities = collections.Counter()
    hopper_stacks = collections.Counter()
    hopper_items = chest_items = 0
    farm_hoppers = farm_chests = 0
    for root in roots(world / "region"):
        for block in root.get("block_entities", []):
            kind = block.get("id", "unknown")
            block_entities[kind] += 1
            x, y, z = block.get("x"), block.get("y"), block.get("z")
            if y != 80 or x is None or z is None or not -8 <= z < 56:
                continue
            items = block.get("Items", [])
            quantity = sum(item.get("count", item.get("Count", 0)) for item in items)
            if kind == "minecraft:hopper" and -8 <= x < 8:
                farm_hoppers += 1
                hopper_stacks[len(items)] += 1
                hopper_items += quantity
            elif kind == "minecraft:chest" and x == 8:
                farm_chests += 1
                chest_items += quantity
    return {"entities": dict(entities), "entities_with_no_ai": dict(no_ai),
            "entities_with_nonpositive_health": dict(nonpositive_health), "block_entities": dict(block_entities),
            "farm_hoppers": farm_hoppers, "farm_chests": farm_chests,
            "farm_hopper_stack_count_distribution": dict(hopper_stacks),
            "farm_hopper_item_count": hopper_items, "farm_chest_item_count": chest_items,
            "limits": "Saved counts, flags and inventory quantities only; not proof of complete gameplay equivalence. Farm coordinates match the current 1.21.1 stress generator."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.world)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "limits"}))


if __name__ == "__main__":
    main()
