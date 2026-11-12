"""
Copies the craft data from your bazaar website file into the mod, so both use the exact same recipes.

Usage (Python 3):
    python tools/import_site_recipes.py path/to/bazaar-v89.html

Writes src/main/resources/bazaartips/recipes.json with:
  recipes   - item -> name, how many 1 craft makes, ingredients (in the site's order), requirement text
  names     - item ID -> display name
  compactor - items a Personal Compactor can make (from the site's Super Compactor list)
IDs are converted to bazaar product IDs: INK_SACK-3 -> INK_SACK:3, SHARPNESS;5 (a book) -> ENCHANTMENT_SHARPNESS_5.
"""
import json
import os
import re
import sys

ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]


def site_object(html, name):
    start = html.index("const " + name + " = ") + len("const " + name + " = ")
    line = html[start:html.index("\n", start)]
    return json.loads(line[:line.rindex("}") + 1])


def main(path):
    html = open(path, encoding="utf-8").read()
    recipes = site_object(html, "RECIPES")
    names = site_object(html, "NAMES")
    compactor = site_object(html, "SC")
    mutations = site_object(html, "MUTATIONS")
    minions = site_object(html, "MINIONS")

    # The site adds two kinds of requirement when it loads; do the same here, in the same order.
    # 1. Recipes using a Garden mutation need it analyzed.
    for rec in recipes.values():
        need = list(dict.fromkeys(mutations[i["id"]] for i in rec.get("ingredients", []) if i["id"] in mutations))
        if need:
            txt = ", ".join(need) + " analyzed"
            rec["req"] = rec["req"] + " · " + txt if rec.get("req") else txt
    # 2. Minion tiers without a requirement share their family's (or the first tier that lists one).
    for key, rec in recipes.items():
        m = re.match(r"^(.*_GENERATOR)_\d+$", key)
        if not m or rec.get("req"):
            continue
        req = (minions.get(m.group(1)) or {}).get("req") or ""
        t = 1
        while not req and t <= 12:
            r = recipes.get(m.group(1) + "_" + str(t))
            if r and r.get("req"):
                req = r["req"]
            t += 1
        if req:
            rec["req"] = req

    def is_book(i):
        m = re.match(r"^(.+);(\d+)$", i)
        return bool(m) and names.get(i.replace("-", ":"), "").startswith("Enchanted Book")

    def bz(i):
        if is_book(i):
            name, level = i.rsplit(";", 1)
            return "ENCHANTMENT_" + name + "_" + level
        return i.replace("-", ":")

    def book_name(i):
        name, level = i.rsplit(";", 1)
        n = int(level)
        return name.replace("_", " ").title() + " " + (ROMAN[n] if n < len(ROMAN) else level)

    out_recipes = {}
    used = set()
    for key, rec in recipes.items():
        k = bz(key)
        ins = [[bz(i["id"]), int(i["qty"])] for i in rec["ingredients"]]
        out_recipes[k] = {"name": book_name(key) if is_book(key) else rec.get("name") or names.get(bz(key), k),
                          "out": int(rec["output"]), "in": ins}
        if rec.get("req"):
            out_recipes[k]["req"] = rec["req"]
        used.add(key)
        used.update(i["id"] for i in rec["ingredients"])

    out_names = {}
    for i in sorted(used):
        n = book_name(i) if is_book(i) else names.get(i.replace("-", ":"))
        if n:
            out_names[bz(i)] = n

    data = {
        "recipes": dict(sorted(out_recipes.items())),
        "names": out_names,
        "compactor": sorted({bz(v["to"]) for v in compactor.values()}),
    }
    dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "bazaartips", "recipes.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(out_recipes)} recipes, {len(out_names)} names, {len(data['compactor'])} compactor items -> {os.path.normpath(dest)}")

    # The NPC and Forge tabs: kept in the site's own ID format (the mod converts them the same way the site does).
    money = site_object(html, "MONEY")
    # Like the site: MONEY.names fills in names NAMES doesn't have (pets lose their "[Lvl {LVL}]" prefix).
    site_names = dict(names)
    for k, v in money.get("names", {}).items():
        key = k.replace("-", ":")
        if not site_names.get(key):
            site_names[key] = re.sub(r"^\[Lvl \{LVL\}\]\s*", "", v)
    money_out = {
        "forge": money["forge"],
        "shops": money["shops"],
        "npcs": money["npcs"],
        "npcAvail": money.get("npcAvail", {}),
        "stack": money.get("stack", {}),
        "siteNames": site_names,
        # Books, Currencies, Minions and Profit / hr tabs
        "enchMax": money.get("enchMax", {}),
        "enchMap": money.get("enchMap", {}),
        "cur": money.get("cur", []),
        # Kat and Bits tabs
        "kat": money.get("kat", []),
        "bits": money.get("bits", []),
        "minions": minions,
        "minProfit": site_object(html, "MINPROFIT"),
        "minStore": site_object(html, "MINSTORE"),
        "comp": site_object(html, "COMP"),
        "sc": compactor,
    }
    dest2 = os.path.join(os.path.dirname(dest), "money.json")
    with open(dest2, "w", encoding="utf-8") as f:
        json.dump(money_out, f, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(money['forge'])} forge recipes, {len(money['shops'])} NPC shop offers, {len(money['npcs'])} NPCs, "
          f"{len(money.get('kat', []))} Kat upgrades, {len(money.get('bits', []))} bits items -> {os.path.normpath(dest2)}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: python tools/import_site_recipes.py bazaar-v89.html")
    main(sys.argv[1])
