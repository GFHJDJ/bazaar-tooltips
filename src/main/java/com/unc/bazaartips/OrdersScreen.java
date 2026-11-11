package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/** Your bazaar orders (read from Manage Orders) and whether each is still the best price. */
public class OrdersScreen extends TableScreen<OrderTracker.Check> {
	public OrdersScreen() {
		super("Orders", "orders");
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				new Control(() -> "Alerts: " + onOff(Settings.orderAlerts),
						() -> "Chat alerts (" + onOff(Settings.orderAlerts) + "): a message when one of your buy orders is outbid or a sell offer is "
								+ "undercut, checked every time prices refresh (about once a minute). Each order alerts once until it's back on top.",
						d -> Settings.orderAlerts = !Settings.orderAlerts),
				new Control(() -> "Forget orders",
						() -> "Clears the list. It fills again the next time you open /bz -> Manage Orders.",
						OrderTracker::clear));
	}

	private static String statusText(OrderTracker.Check c) {
		boolean buy = c.order().type() == OrderTracker.Type.BUY;
		return switch (c.status()) {
			case TOP -> "Top";
			case TIED -> "Tied";
			case OUTBID -> buy ? "Outbid" : "Undercut";
			case FILLED -> "Filled";
			default -> "?";
		};
	}

	private static int statusColor(OrderTracker.Check c) {
		return switch (c.status()) {
			case TOP -> Colors.GREEN;
			case TIED -> Colors.YELLOW;
			case OUTBID -> Colors.RED;
			case FILLED -> Colors.AQUA;
			default -> Colors.GRAY;
		};
	}

	@Override
	protected List<Column<OrderTracker.Check>> columns() {
		return List.of(
				new Column<>("Type", "Type: Buy = a buy order (you're buying), Sell = a sell offer (you're selling).",
						c -> c.order().type() == OrderTracker.Type.BUY ? "Buy" : "Sell", c -> Colors.GRAY),
				new Column<>("Your price", "Your price: the price per item on your order.", c -> Format.price(c.order().price()), c -> Colors.YELLOW),
				new Column<>("Best now", "Best now: the top buy order (for your buy orders) or the lowest sell offer (for your sell offers) on the "
						+ "bazaar right now. If it's better than yours, others get filled first.",
						c -> Double.isNaN(c.best()) ? "-" : Format.price(c.best()), c -> Colors.YELLOW),
				new Column<>("Status", "Status: Top = yours is the best price. Tied = someone else has the same price (whoever placed it first "
						+ "fills first). Outbid / Undercut = someone beat your price. Filled = done; claim it. ? = can't tell (no bazaar match).",
						OrdersScreen::statusText, OrdersScreen::statusColor),
				new Column<>("Filled", "Filled: how many have filled so far, out of the order's amount (as of when you last opened Manage Orders).",
						c -> Format.count(c.order().filled()) + "/" + Format.count(c.order().amount()), c -> Colors.GRAY));
	}

	@Override
	protected String stamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + OrderTracker.updatedAt() + "|" + OrderTracker.orders().size() + "|" + Settings.orderAlerts
				+ "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<OrderTracker.Check> build(String search) {
		String q = search == null ? "" : search.trim().toLowerCase();
		List<OrderTracker.Check> out = new ArrayList<>();
		for (OrderTracker.Check c : OrderTracker.checkAll(BazaarPrices::get)) {
			if (c.order().name().toLowerCase().contains(q)) out.add(c);
		}
		// what needs you first: outbid, then tied, then filled (to claim), then the rest
		out.sort((a, b) -> Integer.compare(rank(a.status()), rank(b.status())));
		return out;
	}

	private static int rank(OrderTracker.Status s) {
		return switch (s) {
			case OUTBID -> 0;
			case TIED -> 1;
			case FILLED -> 2;
			case UNKNOWN -> 3;
			default -> 4;
		};
	}

	@Override
	protected String waiting() {
		return null;
	}

	@Override
	protected String name(OrderTracker.Check c) {
		return Format.count(c.order().amount()) + "x " + c.order().name();
	}

	@Override
	protected int nameColor(OrderTracker.Check c) {
		return c.status() == OrderTracker.Status.OUTBID ? Colors.RED : Colors.WHITE;
	}

	@Override
	protected String tooltip(OrderTracker.Check c) {
		OrderTracker.Order o = c.order();
		boolean buy = o.type() == OrderTracker.Type.BUY;
		String s = switch (c.status()) {
			case OUTBID -> (buy ? "Outbid: the top buy order is " : "Undercut: the lowest sell offer is ") + Format.price(c.best())
					+ " · re-place yours at " + Format.price(buy ? CraftEngine.round1(c.best() + 0.1) : Math.max(0.1, CraftEngine.round1(c.best() - 0.1)))
					+ " to be first again";
			case TIED -> "Tied with " + (c.ordersAtBest() - 1) + " other order" + (c.ordersAtBest() == 2 ? "" : "s") + " at " + Format.price(c.best());
			case TOP -> "Yours is the best price";
			case FILLED -> "Filled: claim it in Manage Orders";
			default -> o.productId() == null ? "Couldn't match '" + o.name() + "' to a bazaar item" : "No orders on that side of the book";
		};
		if (o.claimable() > 0) s += " · " + Format.count(o.claimable()) + " to claim";
		return s;
	}

	@Override
	protected void open(OrderTracker.Check c) {
		if (c.order().productId() != null) minecraft.gui.setScreen(new PriceDetailScreen(this, c.order().productId()));
	}

	@Override
	protected String itemExplanation() {
		return "Your orders, the ones that need you first at the top (outbid, tied, filled). Click one for that item's live order book.";
	}

	@Override
	protected String noun() {
		return "orders";
	}

	@Override
	protected String hint() {
		long t = OrderTracker.updatedAt();
		return t == 0 ? "Open /bz -> Manage Orders once and your orders show up here"
				: "Read from Manage Orders " + Format.age(System.currentTimeMillis() - t) + " ago · reopen it after you change orders";
	}

	@Override
	protected String emptyText() {
		return "No orders yet: open /bz, then Manage Orders, and they'll show up here.";
	}
}
