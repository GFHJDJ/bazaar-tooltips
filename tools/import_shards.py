"""
Copies the shard fusion recipes from SkyShards (https://github.com/Campionnn/SkyShards, MIT licensed, by Campion)
into the mod, in a smaller format.

Usage (Python 3):
    python tools/import_shards.py path/to/fusion-data.json
(get it from https://raw.githubusercontent.com/Campionnn/SkyShards/master/public/fusion-data.json)

Writes src/main/resources/bazaartips/shards.json:
  shards  - code -> [bazaar ID, name, rarity, how many one fusion uses, family]
  recipes - result code -> {how many one fusion makes: "A B,A B,..."} (the two shards fused)
"""
import json
import os
import sys


def main(path):
    d = json.load(open(path, encoding="utf-8"))
    shards = {k: [v["internal_id"], v["name"], v.get("rarity") or "", int(v.get("fuse_amount") or 5), v.get("family") or ""]
              for k, v in d["shards"].items() if v.get("internal_id")}
    recipes = {}
    pairs = 0
    for out, by_qty in d["recipes"].items():
        if out not in shards:
            continue
        r = {}
        for qty, lst in by_qty.items():
            ok = [a + " " + b for a, b in lst if a in shards and b in shards]
            if ok:
                r[str(int(qty))] = ",".join(ok)
                pairs += len(ok)
        if r:
            recipes[out] = r
    data = {"source": "SkyShards fusion-data.json by Campion (MIT)", "shards": shards, "recipes": recipes}
    dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "bazaartips", "shards.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(shards)} shards, {len(recipes)} results, {pairs} fusions -> {os.path.normpath(dest)}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: python tools/import_shards.py fusion-data.json")
    main(sys.argv[1])
