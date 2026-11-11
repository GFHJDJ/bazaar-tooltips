package com.unc.bazaartips;

/** A tiny copy of the site's data (forge recipes, NPC shops, crafts) for tests. */
final class TestData {
	private TestData() {}

	static final String SITE = """
			{"forge":[
			   {"id":"REFINED_DIAMOND","n":1,"t":28800,"in":[["ENCHANTED_DIAMOND_BLOCK",2]],"hotm":2},
			   {"id":"TOP_TIER_THING","n":1,"t":3600,"in":[["DIAMOND",1]],"hotm":7}],
			 "shops":[{"npc":"FARM_MERCHANT","id":"WHEAT","q":3,"c":7,"in":[]}],
			 "npcs":{"FARM_MERCHANT":["Farm Merchant","Hub"]},
			 "npcAvail":{},"stack":{},"siteNames":{}}""";

	static final String RECIPES = """
			{"recipes":{"ENCHANTED_DIAMOND":{"name":"Enchanted Diamond","out":1,"in":[["DIAMOND",160]]}},
			 "names":{"DIAMOND":"Diamond"}}""";

	static void load() {
		SiteData.load(SITE);
		Recipes.load(RECIPES);
	}
}
