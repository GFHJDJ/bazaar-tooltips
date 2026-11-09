package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Flip alerts: after each price refresh, a chat message for each flip that has just reached your alert score.
 * The first check only counts what's already there (no flood of messages). Risky and low-volume flips never alert,
 * and a flip only alerts once its price history has loaded (so the score is the full one). No Minecraft code.
 */
public final class FlipAlerts {
	private FlipAlerts() {}

	/** At most this many messages per price refresh. */
	public static final int MAX_PER_CHECK = 3;
	/** The same item doesn't alert again for this long. */
	public static final long QUIET_MILLIS = 60 * 60 * 1000;
	/** A new flip still waiting for its price history is given up on after this long. */
	public static final long PENDING_MILLIS = 15 * 60 * 1000;

	/** What one check found: new flips to announce, how many qualify on a first check (-1 otherwise), and items whose history to fetch. */
	public record Result(List<Flipper.Listed> alerts, int firstCount, List<String> needHistory) {}

	private static Set<String> above = null;                  // flips at/above the score last time (null = not started)
	private static int startedFor = -1;
	private static final Map<String, Long> pending = new HashMap<>(), alertedAt = new HashMap<>();

	public static synchronized void reset() {
		above = null;
		startedFor = -1;
		pending.clear();
		alertedAt.clear();
	}

	/** Whether the score is the full one: today's move and the price history have both been fetched. */
	static boolean ready(CoflData.Move mv, CoflData.Hist h) {
		return mv != null && h != null;
	}

	public static synchronized Result check(List<Flipper.Flip> flips, int minScore, Function<String, CoflData.Move> moves,
			Function<String, CoflData.Hist> hists, long now) {
		if (minScore != startedFor) {
			above = null;
			pending.clear();
			startedFor = minScore;
		}
		Set<String> now_above = new HashSet<>();
		Map<String, Flipper.Listed> ready = new HashMap<>();
		List<Flipper.Listed> waiting = new ArrayList<>();
		for (Flipper.Flip f : flips) {
			if (f.risk().level() == Risk.Level.HIGH || f.weekVol() < CraftEngine.LOW_VOLUME) continue;
			CoflData.Move mv = moves.apply(f.id());
			CoflData.Hist h = hists.apply(f.id());
			Flipper.Score s = Flipper.score(f, mv, h);
			if (s.score() < minScore) continue;
			now_above.add(f.id());
			Flipper.Listed l = new Flipper.Listed(f, s, null);
			if (ready(mv, h)) ready.put(f.id(), l);
			else waiting.add(l);
		}
		waiting.sort((a, b) -> Integer.compare(b.score().score(), a.score().score()));
		boolean first = above == null;
		if (!first) for (String id : now_above) if (!above.contains(id)) pending.putIfAbsent(id, now);
		above = now_above;
		// price history to fetch: new flips first (they're waiting on it to alert), then the best-scoring others
		List<String> need = new ArrayList<>();
		for (Flipper.Listed l : waiting) if (pending.containsKey(l.flip().id())) need.add(l.flip().id());
		for (Flipper.Listed l : waiting) if (need.size() < 10 && !need.contains(l.flip().id())) need.add(l.flip().id());
		if (first) return new Result(List.of(), ready.size() + waiting.size(), need);   // first check: just count
		List<Flipper.Listed> out = new ArrayList<>();
		for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			String id = e.getKey();
			if (!now_above.contains(id) || now - e.getValue() > PENDING_MILLIS) { it.remove(); continue; }
			Flipper.Listed l = ready.get(id);
			if (l == null) continue;                          // still waiting for its history
			it.remove();
			Long last = alertedAt.get(id);
			if (last != null && now - last < QUIET_MILLIS) continue;
			out.add(l);
		}
		out.sort((a, b) -> Integer.compare(b.score().score(), a.score().score()));
		if (out.size() > MAX_PER_CHECK) out = new ArrayList<>(out.subList(0, MAX_PER_CHECK));
		for (Flipper.Listed l : out) alertedAt.put(l.flip().id(), now);
		return new Result(out, -1, need);
	}

	/** The chat text: {headline, detail}. */
	public static String[] text(Flipper.Listed l) {
		Flipper.Flip f = l.flip();
		return new String[] {"Flip " + l.score().score() + ": ", f.name() + " · " + Format.signedCoins(f.each()) + " each ("
				+ String.format(java.util.Locale.US, "%.1f%%", f.pct()) + ") · buy order " + Format.price(f.buyAt()) + ", sell offer "
				+ Format.price(f.sellAt()) + " · ~" + Format.signedShortCoins(f.perHour()) + "/hr"};
	}
}
