/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Side panel: pick content and a boss, see the wiki's best-in-slot setup for
 * each combat style, what you already own, and what to buy next.
 */
public class GearUpgradePanel extends PluginPanel
{
	/** Item names and headings. */
	private static final Font NAME_FONT = FontManager.getRunescapeFont().deriveFont(17f);

	/** Supporting text - deliberately close to the name size to stay readable. */
	private static final Font DETAIL_FONT = FontManager.getRunescapeFont().deriveFont(15f);
	/** Already sorted. */
	private static final Color OWNED = ColorScheme.PROGRESS_COMPLETE_COLOR;

	/** Blocked - levels not met, or not enough coins. */
	private static final Color MISSING = ColorScheme.PROGRESS_ERROR_COLOR;

	/** Obtainable now: affordable on the GE, or earned from content. */
	private static final Color OBTAINABLE = ColorScheme.PROGRESS_INPROGRESS_COLOR;

	/**
	 * Usable width inside the panel, after its own borders and the scrollbar.
	 * Everything is constrained to this: the tab content never scrolls
	 * sideways, so anything wider is silently clipped instead of wrapping.
	 */
	private static final int CONTENT_WIDTH = PANEL_WIDTH - 34;

	/** Icon column width used by the item rows. */
	private static final int ICON_WIDTH = 38;

	/** Ladder entries before the hover list splits into two columns. */
	private static final int TOOLTIP_COLUMN_THRESHOLD = 12;

	/** Width available for wrapped text sitting beside an icon. */
	private static final int TEXT_WIDTH = CONTENT_WIDTH - ICON_WIDTH - 24;

	private static final String EXPAND_CLOSED = "▸";
	private static final String EXPAND_OPEN = "▾";



	private final ItemManager itemManager;

	private final JComboBox<String> tierSelect = new JComboBox<>();
	private final JComboBox<String> groupSelect = new JComboBox<>();
	private final JComboBox<String> monsterSelect = new JComboBox<>();
	private final JTextArea budgetLabel =
		PanelText.body("", NAME_FONT, ColorScheme.GRAND_EXCHANGE_PRICE);
	private final JTextArea statusLabel =
		PanelText.body("", DETAIL_FONT, ColorScheme.PROGRESS_INPROGRESS_COLOR);
	private final JPanel notesPanel = new JPanel();
	private final JTabbedPane tabs = new JTabbedPane();

	private final Map<CombatStyle, StyleTab> styleTabs = new EnumMap<>(CombatStyle.class);

	private Consumer<String> onTierSelected = name -> {
	};
	private Consumer<String> onGroupSelected = name -> {
	};
	private Consumer<String> onMonsterSelected = name -> {
	};

	private boolean suppressEvents;

	/**
	 * The contents of one combat style tab.
	 */
	private static class StyleTab
	{
		private final JPanel root = new JPanel();
		private JScrollPane scroll;
		private final EquipmentGrid grid;
		private final JPanel detail = new JPanel(new BorderLayout());
		private final JPanel summary = new JPanel(new GridLayout(0, 2, 4, 3));
		private final JPanel bisSection = new JPanel();
		private final JPanel buySection = new JPanel();

		StyleTab(ItemManager itemManager)
		{
			grid = new EquipmentGrid(itemManager);

			root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
			root.setBackground(ColorScheme.DARK_GRAY_COLOR);
			root.setBorder(BorderFactory.createEmptyBorder(6, 6, 10, 6));

			summary.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			summary.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			summary.setAlignmentX(Component.LEFT_ALIGNMENT);
			summary.setMaximumSize(new Dimension(CONTENT_WIDTH, 110));

			bisSection.setLayout(new BoxLayout(bisSection, BoxLayout.Y_AXIS));
			bisSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
			bisSection.setAlignmentX(Component.LEFT_ALIGNMENT);

			buySection.setLayout(new BoxLayout(buySection, BoxLayout.Y_AXIS));
			buySection.setBackground(ColorScheme.DARK_GRAY_COLOR);
			buySection.setAlignmentX(Component.LEFT_ALIGNMENT);

			detail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			detail.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
			detail.setAlignmentX(Component.LEFT_ALIGNMENT);
			detail.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
			detail.setVisible(false);

			grid.setOnSelect(item -> showItemDetail(detail, item));
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			grid.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));

			root.add(grid);
			root.add(detail);
			root.add(summary);
			root.add(bisSection);
			root.add(buySection);

			grid.showEmpty();
		}
	}

	/**
	 * Fills the detail strip under the grid when a worn item is clicked.
	 */
	private static void showItemDetail(JPanel detail, EquipmentItem item)
	{
		detail.removeAll();

		if (item == null)
		{
			detail.setVisible(false);
			return;
		}

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		text.add(PanelText.body(item.getName(), NAME_FONT, Color.WHITE));

		text.add(PanelText.body(item.isUntradeable()
			? "Untradeable"
			: QuantityFormatter.quantityToStackSize(item.getPrice()) + " gp on the GE",
			DETAIL_FONT, ColorScheme.GRAND_EXCHANGE_PRICE));

		final GearStats stats = new GearStats();
		stats.add(item.getStats());

		text.add(PanelText.body(
			"Stab " + signed(stats.getAstab())
				+ ", slash " + signed(stats.getAslash())
				+ ", crush " + signed(stats.getAcrush())
				+ "\nRange " + signed(stats.getArange())
				+ ", magic " + signed(stats.getAmagic())
				+ "\nStr " + signed(stats.getStr())
				+ ", rng str " + signed(stats.getRstr())
				+ ", prayer " + signed(stats.getPrayer()),
			DETAIL_FONT, ColorScheme.LIGHT_GRAY_COLOR));

		detail.add(text, BorderLayout.CENTER);
		detail.setVisible(true);
		detail.revalidate();
		detail.repaint();
	}

	private static String signed(int value)
	{
		return (value > 0 ? "+" : "") + value;
	}

	public GearUpgradePanel(ItemManager itemManager)
	{
		super(false);
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);

		for (CombatStyle style : CombatStyle.values())
		{
			final StyleTab tab = new StyleTab(itemManager);
			styleTabs.put(style, tab);

			final JScrollPane scroll = new JScrollPane(tab.root,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scroll.getVerticalScrollBar().setUnitIncrement(16);
			scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
			tab.scroll = scroll;

			tabs.addTab(style.getDisplayName(), scroll);
		}

		// Each tab keeps its own scroll position, so switching from halfway down
		// one tab lands halfway down the next. Always start at the top.
		tabs.addChangeListener(e -> scrollSelectedTabToTop());

		add(tabs, BorderLayout.CENTER);
	}

	private JPanel buildHeader()
	{
		final JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		header.add(fieldLabel("Difficulty"));
		tierSelect.setFocusable(false);
		tierSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
		tierSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		tierSelect.addActionListener(e ->
		{
			if (suppressEvents)
			{
				return;
			}
			final Object selected = tierSelect.getSelectedItem();
			if (selected != null)
			{
				onTierSelected.accept(selected.toString());
			}
		});
		header.add(tierSelect);

		header.add(fieldLabel("Content"));
		groupSelect.setFocusable(false);
		groupSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		groupSelect.addActionListener(e ->
		{
			if (suppressEvents)
			{
				return;
			}
			final Object selected = groupSelect.getSelectedItem();
			if (selected != null)
			{
				onGroupSelected.accept(selected.toString());
			}
		});
		header.add(groupSelect);

		header.add(fieldLabel("Boss"));
		monsterSelect.setFocusable(false);
		monsterSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		monsterSelect.addActionListener(e ->
		{
			if (suppressEvents)
			{
				return;
			}
			final Object selected = monsterSelect.getSelectedItem();
			if (selected != null)
			{
				// New boss means entirely new content, so start from the top.
				scrollSelectedTabToTop();
				onMonsterSelected.accept(selected.toString());
			}
		});
		header.add(monsterSelect);

		budgetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		budgetLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		header.add(budgetLabel);

		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(statusLabel);

		notesPanel.setLayout(new BoxLayout(notesPanel, BoxLayout.Y_AXIS));
		notesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		notesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(notesPanel);

		return header;
	}

	private static JLabel fieldLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(DETAIL_FONT);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		return label;
	}

	/**
	 * Returns the visible tab to the top. Deferred, because the scrollbar range
	 * is only correct once the newly shown tab has been laid out.
	 */
	private void scrollSelectedTabToTop()
	{
		final Component selected = tabs.getSelectedComponent();
		if (!(selected instanceof JScrollPane))
		{
			return;
		}

		final JScrollPane scroll = (JScrollPane) selected;
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
	}

	private static JTextArea sectionHeader(String text)
	{
		// Wrapping rather than a JLabel: an unwrapped label reports its full
		// string width as its minimum, which widens the whole sidebar.
		final JTextArea label = PanelText.body(text, NAME_FONT, ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(12, 2, 6, 2));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public void setOnTierSelected(Consumer<String> listener)
	{
		this.onTierSelected = listener;
	}

	public void setOnGroupSelected(Consumer<String> listener)
	{
		this.onGroupSelected = listener;
	}

	public void setTiers(List<String> tiers, String selected)
	{
		suppressEvents = true;
		tierSelect.removeAllItems();
		for (String tier : tiers)
		{
			tierSelect.addItem(tier);
		}
		if (selected != null)
		{
			tierSelect.setSelectedItem(selected);
		}
		suppressEvents = false;
	}

	public void setOnMonsterSelected(Consumer<String> listener)
	{
		this.onMonsterSelected = listener;
	}

	public void setGroups(List<String> groups, String selected)
	{
		suppressEvents = true;
		groupSelect.removeAllItems();
		for (String group : groups)
		{
			groupSelect.addItem(group);
		}
		if (selected != null)
		{
			groupSelect.setSelectedItem(selected);
		}
		// A tier with a single piece of content has nothing to choose between.
		groupSelect.setEnabled(groups.size() > 1);
		suppressEvents = false;
	}

	public void setMonsters(List<String> names, String selected)
	{
		suppressEvents = true;
		monsterSelect.removeAllItems();
		for (String name : names)
		{
			monsterSelect.addItem(name);
		}
		if (selected != null)
		{
			monsterSelect.setSelectedItem(selected);
		}
		// A standalone boss has nothing to choose between.
		monsterSelect.setEnabled(names.size() > 1);
		suppressEvents = false;
	}

	public void setBudget(long coins, boolean overridden, boolean bankSeen)
	{
		// Plain text: these are text areas now, so HTML would be shown literally.
		budgetLabel.setText("You have " + QuantityFormatter.quantityToStackSize(coins) + " gp"
			+ (overridden ? " (override)" : bankSeen ? "" : " (inventory only)"));
		budgetLabel.setForeground(bankSeen || overridden ? ColorScheme.GRAND_EXCHANGE_PRICE
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
	}

	public void setStatus(String status)
	{
		statusLabel.setText(status == null ? "" : status);
		statusLabel.setVisible(status != null && !status.isEmpty());
	}

	/**
	 * Effect-based gear advice for the selected boss, kept apart from the
	 * ranking because these items usually cost damage.
	 */
	public void setNotes(List<GearNote> notes)
	{
		notesPanel.removeAll();

		if (!notes.isEmpty())
		{
			notesPanel.add(sectionHeader("Worth bringing anyway"));
			for (GearNote note : notes)
			{
				notesPanel.add(noteRow(note.getItem(), note.getReason(), ColorScheme.BRAND_ORANGE));
			}
		}

		notesPanel.revalidate();
		notesPanel.repaint();
	}

	/**
	 * A note as a collapsed section: the title is always visible, the
	 * explanation opens on click. Several of these run to a paragraph, which
	 * buries the actual rankings if left expanded.
	 */
	private static JPanel noteRow(String title, String body, Color titleColour)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		// Never let a row's minimum width push the sidebar wider.
		row.setMinimumSize(new Dimension(1, 1));

		final boolean hasBody = body != null && !body.isEmpty();

		// Titles run to a full sentence in places, so this wraps too rather than
		// widening the panel.
		final JTextArea header = PanelText.body(
			(hasBody ? EXPAND_CLOSED + " " : "") + title, NAME_FONT, titleColour);
		row.add(header, BorderLayout.NORTH);

		if (!hasBody)
		{
			return row;
		}

		final JTextArea reason = PanelText.body(body, DETAIL_FONT, ColorScheme.LIGHT_GRAY_COLOR);
		reason.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		reason.setVisible(false);
		row.add(reason, BorderLayout.CENTER);

		header.setCursor(new Cursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				final boolean open = !reason.isVisible();
				reason.setVisible(open);
				header.setText((open ? EXPAND_OPEN : EXPAND_CLOSED) + " " + title);
				row.revalidate();
				row.repaint();
			}
		});

		return row;
	}

	/**
	 * Renders the wiki best-in-slot checklist for a style.
	 */
	/**
	 * Replaces the computed summary with one measured from the wiki setup the
	 * player actually owns, so the diagram, the numbers and the list agree.
	 */
	public void showBisSummary(CombatStyle style, DpsCalculator.Result dps, DamageType type)
	{
		final StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}

		tab.summary.removeAll();
		addStat(tab.summary, "DPS", String.format("%.2f", dps.getDps()));
		addStat(tab.summary, "Max hit", String.valueOf(dps.getMaxHit()));
		addStat(tab.summary, "Accuracy", String.format("%.1f%%", dps.getAccuracy() * 100));
		addStat(tab.summary, "Attack type", type.getDisplayName());
		tab.summary.revalidate();
		tab.summary.repaint();
	}

	public void showBis(CombatStyle style, BisAnalyser.Result result, long budget)
	{
		final StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}

		// The panel rebuilds on every bank change. Without this the view jumps -
		// removing and re-adding components makes the scroll pane settle
		// somewhere arbitrary, which looks like the plugin scrolling for you.
		final int scrollPos = tab.scroll == null ? 0 : tab.scroll.getVerticalScrollBar().getValue();

		// The grid reflects the wiki setup for THIS boss, not the computed
		// loadout - otherwise the diagram and the list below it disagree.
		tab.grid.update(result.getWornFromBis());

		tab.bisSection.removeAll();

		// The single most useful thing on the page, so it goes first. A per-slot
		// list cannot say that one weapon beats three armour pieces put together.
		// Always shown, even when the answer is "nothing" - a missing section
		// reads as a bug rather than as an answer.
		tab.bisSection.add(sectionHeader("Recommended next purchase"));

		final BisAnalyser.Recommendation best = result.getBestBuy();
		if (best == null)
		{
			tab.bisSection.add(noteRow("You have best in slot",
				"Nothing in this setup would improve on what you are already wearing.",
				OWNED));
		}
		else if (!best.isPurchasable())
		{
			// Still the next step, just not one you can buy.
			final StringBuilder earned = new StringBuilder("Earned from content, not sold on "
				+ "the Grand Exchange - it is still your next upgrade here.");
			earned.append("\nGoes in the ").append(best.getSlotName().toLowerCase(Locale.ROOT))
				.append(" slot.");
			if (best.getStatNote() != null)
			{
				earned.append(" Worth ").append(best.getStatNote()).append('.');
			}
			tab.bisSection.add(noteRow(best.getItem().getName(), earned.toString(),
				ColorScheme.BRAND_ORANGE));
		}
		else
		{
			final StringBuilder detail = new StringBuilder();
			detail.append(best.getCost() > 0
				? QuantityFormatter.quantityToStackSize(best.getCost()) + " gp"
				: "not on the GE");

			if (best.getShortfall() > 0)
			{
				detail.append(" - need ")
					.append(QuantityFormatter.quantityToStackSize(best.getShortfall()))
					.append(" more");
			}

			// Against a target with no Defence the rounded DPS often does not move,
			// so quote the stat change instead of a meaningless "+0.0%".
			if (best.getPercentGain() >= 0.05)
			{
				detail.append(String.format(" - biggest damage gain available (+%.1f%%)",
					best.getPercentGain()));
			}
			else if (best.getStatNote() != null)
			{
				detail.append(" - biggest stat gain available (")
					.append(best.getStatNote()).append(')');
			}

			detail.append("\nGoes in the ").append(best.getSlotName().toLowerCase(Locale.ROOT))
				.append(" slot.");

			final BisAnalyser.Recommendation value = result.getBestValue();
			if (value != null && value.getCost() > 0)
			{
				detail.append("\nBest value per gp: ").append(value.getItem().getName())
					.append(" at ")
					.append(QuantityFormatter.quantityToStackSize(value.getCost()))
					.append(String.format(" gp (+%.1f%%)", value.getPercentGain()));
			}

			tab.bisSection.add(noteRow(best.getItem().getName(), detail.toString(),
				best.getShortfall() > 0 ? MISSING : OBTAINABLE));
		}

		tab.bisSection.add(sectionHeader("Best in slot (wiki)"));

		final BisSetup setup = result.getSetup();

		if (setup.isNeedsReview())
		{
			tab.bisSection.add(noteRow("Unverified data",
				"This setup has not been checked by hand yet - treat it as a draft.", MISSING));
		}

		if (setup.getNote() != null && !setup.getNote().isEmpty())
		{
			tab.bisSection.add(noteRow("Method", setup.getNote(), ColorScheme.BRAND_ORANGE));
		}

		for (BisAnalyser.SlotStatus slot : result.getSlots())
		{
			tab.bisSection.add(bisRow(slot));
		}

		final List<BisAnalyser.Step> specs = result.getSpecWeapons();
		if (specs != null && !specs.isEmpty())
		{
			tab.bisSection.add(sectionHeader("Special attack weapons"));

			// Lead with the best one still missing, since that is the actionable
			// part; the full tier list is on hover.
			BisAnalyser.Step firstMissing = null;
			int owned = 0;
			for (BisAnalyser.Step step : specs)
			{
				if (step.isOwned())
				{
					owned++;
				}
				else if (firstMissing == null && step.getLevelShortfall() == null)
				{
					firstMissing = step;
				}
			}

			final String detail;
			if (owned == specs.size())
			{
				detail = "You have all of these. Brought in addition to the worn setup.";
			}
			else if (firstMissing == null)
			{
				detail = "Brought in addition to the worn setup. Hover for the full list.";
			}
			else
			{
				detail = "Best one you are missing: " + firstMissing.getName()
					+ (firstMissing.getPrice() > 0
						? " - " + QuantityFormatter.quantityToStackSize(firstMissing.getPrice())
							+ " gp"
						: " - not on the GE")
					+ ". Brought in addition to the worn setup.";
			}

			final JPanel specRow = noteRow(
				owned + " of " + specs.size() + " owned", detail, Color.WHITE);
			applyTooltip(specRow, stepListTooltip("Special attack weapons", specs));
			tab.bisSection.add(specRow);
		}

		final BisAnalyser.SlotStatus next = result.getNextBuy();
		if (next != null && next.getNextBuy() != null)
		{
			tab.bisSection.add(sectionHeader("Buy this next"));
			tab.bisSection.add(noteRow(next.getNextBuy().getName(),
				QuantityFormatter.quantityToStackSize(next.getCost())
					+ " gp - you can afford this now.", OWNED));
		}

		tab.root.revalidate();
		tab.root.repaint();

		if (tab.scroll != null)
		{
			SwingUtilities.invokeLater(
				() -> tab.scroll.getVerticalScrollBar().setValue(scrollPos));
		}
	}

	private JPanel bisRow(BisAnalyser.SlotStatus slot)
	{
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(0, 0, 4, 0),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		row.setMinimumSize(new Dimension(1, 1));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Show the item the row is telling you to act on. If there is something to
		// buy, that is the purchase - naming and picturing the best-in-slot item
		// while the text said "buy a Dragon hunter lance" was simply confusing.
		final EquipmentItem shown;
		if (slot.isHasBest())
		{
			shown = slot.getTarget() != null ? slot.getTarget() : slot.getOwned();
		}
		else if (slot.getAffordable() != null)
		{
			// What they can actually buy today outranks what they cannot.
			shown = slot.getAffordable();
		}
		else if (slot.getNextBuy() != null)
		{
			shown = slot.getNextBuy();
		}
		else
		{
			shown = slot.getTarget() != null ? slot.getTarget() : slot.getOwned();
		}

		if (shown != null)
		{
			final JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(ICON_WIDTH, 36));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			itemManager.getImage(shown.getId()).addTo(icon);
			row.add(icon, BorderLayout.WEST);
		}

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Colour the item name itself, not just the status underneath - that is
		// what gets scanned when reading down the list.
		final JTextArea name = PanelText.body(
			shown == null ? slot.getSlotName() : shown.getName(), NAME_FONT,
			stateColour(slot));
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		// Name and detail share the state colour so a whole row reads as one
		// status at a glance.
		final JTextArea state =
			PanelText.body(describeState(slot), DETAIL_FONT, stateColour(slot));
		state.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(state);

		if (slot.getNote() != null && !slot.getNote().isEmpty())
		{
			final JTextArea note =
				PanelText.body(slot.getNote(), DETAIL_FONT, ColorScheme.LIGHT_GRAY_COLOR);
			note.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(note);
		}

		row.add(text, BorderLayout.CENTER);

		String tooltip = progressionTooltip(slot);
		if (tooltip == null && slot.getTarget() != null)
		{
			tooltip = "<html><b>" + slot.getSlotName() + "</b><br>Best: "
				+ slot.getTarget().getName() + "</html>";
		}

		// Swing does not pass a tooltip down to child components, and the icon and
		// the text between them cover the whole row - so setting it only on the
		// row means there is nowhere left to hover.
		applyTooltip(row, tooltip);

		return row;
	}

	/**
	 * Sets the same tooltip on a component and everything inside it.
	 */
	private static void applyTooltip(JComponent component, String tooltip)
	{
		if (tooltip == null)
		{
			return;
		}

		component.setToolTipText(tooltip);
		for (Component child : component.getComponents())
		{
			if (child instanceof JComponent)
			{
				applyTooltip((JComponent) child, tooltip);
			}
		}
	}

	/**
	 * Colour by what the player can actually do about a slot right now:
	 * green when it is already sorted, amber when it is obtainable, red when
	 * something blocks it - levels not met, or not enough coins.
	 */
	private static Color stateColour(BisAnalyser.SlotStatus slot)
	{
		if (slot.isHasBest())
		{
			return OWNED;
		}
		// Something in reach today, even if the top pick is not. This slot is
		// actionable, so it must not read as blocked.
		if (slot.getAffordable() != null)
		{
			return OBTAINABLE;
		}
		if (slot.getLevelShortfall() != null)
		{
			return MISSING;
		}
		if (slot.getShortfall() > 0)
		{
			return MISSING;
		}
		return OBTAINABLE;
	}

	/**
	 * The whole ladder for a slot as an HTML tooltip: what you own, what sits
	 * above it, what sits below. The row itself only has space for one
	 * suggestion, which hides the fact that Virtus sits between an Ahrim's hood
	 * and an Ancestral hat.
	 */
	private static String progressionTooltip(BisAnalyser.SlotStatus slot)
	{
		final List<BisAnalyser.Step> all = slot.getProgression();

		// On a boss with a clear melee weakness, the weapon list is far more use
		// filtered to that attack type - a crush boss wants the crush tier, not
		// every melee weapon in the game interleaved.
		if (slot.getAttackFocus() != null && all != null)
		{
			final List<BisAnalyser.Step> matching = new java.util.ArrayList<>();
			for (BisAnalyser.Step step : all)
			{
				if (slot.getAttackFocus().equals(step.getAttackType()))
				{
					matching.add(step);
				}
			}

			// Below a couple of entries the filter hides more than it helps.
			if (matching.size() >= 2)
			{
				return stepListTooltip(
					capitalise(slot.getAttackFocus()) + " weapons - what this boss is weak to",
					matching);
			}
		}

		return stepListTooltip(prettySlot(slot.getSlotName()) + " progression", all);
	}

	private static String capitalise(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/**
	 * A titled list of ladder rungs as an HTML tooltip.
	 */
	private static String stepListTooltip(String title, List<BisAnalyser.Step> steps)
	{
		if (steps == null || steps.isEmpty())
		{
			return null;
		}

		// Swing tooltips cannot scroll, and a weapon ladder runs to 23 entries, so
		// a long list wraps into two columns rather than running off the screen.
		final boolean twoColumns = steps.size() > TOOLTIP_COLUMN_THRESHOLD;
		final int rows = twoColumns ? (steps.size() + 1) / 2 : steps.size();

		final StringBuilder sb = new StringBuilder("<html><body>");
		sb.append("<b>").append(title).append("</b>");
		sb.append("<table cellpadding=0 cellspacing=0>");

		for (int row = 0; row < rows; row++)
		{
			sb.append("<tr><td>").append(describeStep(steps.get(row))).append("</td>");

			if (twoColumns)
			{
				final int second = row + rows;
				sb.append("<td width=14></td><td>");
				if (second < steps.size())
				{
					sb.append(describeStep(steps.get(second)));
				}
				sb.append("</td>");
			}
			sb.append("</tr>");
		}

		sb.append("</table></body></html>");
		return sb.toString();
	}

	/**
	 * One ladder entry: ticked when owned, red with the requirement when the
	 * levels are not there, priced otherwise.
	 */
	private static String describeStep(BisAnalyser.Step step)
	{
		if (step.isOwned())
		{
			return "<font color='#3ec72c'>&#10003; " + step.getName() + "</font>";
		}
		if (step.getLevelShortfall() != null)
		{
			return "<font color='#d8534f'>" + step.getName()
				+ " &ndash; needs " + step.getLevelShortfall() + "</font>";
		}
		if (step.getPrice() > 0)
		{
			return step.getName() + " &ndash; "
				+ QuantityFormatter.quantityToStackSize(step.getPrice());
		}
		return step.getName() + " &ndash; <i>not on the GE</i>";
	}

	/** "HEAD" reads better as "Head" in a tooltip heading. */
	private static String prettySlot(String slotName)
	{
		if (slotName == null || slotName.isEmpty())
		{
			return "Slot";
		}
		return slotName.charAt(0) + slotName.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * "Train 5 Attack for an Abyssal tentacle" - only shown when the item is
	 * already affordable and the levels are the single thing in the way.
	 */
	private static void appendTrainingTip(StringBuilder sb, BisAnalyser.SlotStatus slot)
	{
		if (slot.getTrainFor() == null || slot.getTrainNeed() == null)
		{
			return;
		}

		sb.append("\nTrain to ").append(slot.getTrainNeed())
			.append(" to use ").append(slot.getTrainFor().getName());
	}

	/**
	 * Describes the player's position in this slot, given the row is titled with
	 * the best-in-slot item.
	 */
	private static String describeState(BisAnalyser.SlotStatus slot)
	{
		if (slot.isHasBest())
		{
			return "✓ owned";
		}

		final StringBuilder sb = new StringBuilder();
		final EquipmentItem buy = slot.getNextBuy();
		final String target = slot.getTarget() == null ? null : slot.getTarget().getName();

		// Something within budget beats naming a purchase they cannot make. Lead
		// with it, then say what the slot is ultimately working towards.
		if (slot.getAffordable() != null)
		{
			sb.append(QuantityFormatter.quantityToStackSize(slot.getAffordable().getPrice()))
				.append(" gp - affordable now");

			if (target != null && !slot.getAffordable().getName().equals(target))
			{
				sb.append("\nWorking towards ").append(target);
				if (slot.getCost() > 0)
				{
					sb.append(" (").append(QuantityFormatter.quantityToStackSize(slot.getCost()))
						.append(")");
				}
			}

			sb.append(slot.getOwned() != null
				? "\nYou have " + slot.getOwned().getName()
				: "\nNothing owned in this slot");

			appendTrainingTip(sb, slot);
			return sb.toString();
		}

		// The row is titled with the purchase when there is one, so lead with its
		// price and mention what you already have underneath.
		if (buy != null && slot.getLevelShortfall() == null)
		{
			sb.append(QuantityFormatter.quantityToStackSize(slot.getCost())).append(" gp");
			if (slot.getShortfall() > 0)
			{
				sb.append(" - need ")
					.append(QuantityFormatter.quantityToStackSize(slot.getShortfall()))
					.append(" more");
			}

			if (target != null && !buy.getName().equals(target))
			{
				sb.append("\nBest here is ").append(target);
			}
			sb.append(slot.getOwned() != null
				? "\nYou have " + slot.getOwned().getName()
				: "\nNothing owned in this slot");
			appendTrainingTip(sb, slot);
			return sb.toString();
		}

		// Built rather than bought - quote the parts and what they come to.
		if (slot.getCraftNote() != null && slot.getLevelShortfall() == null)
		{
			if (slot.getCost() > 0)
			{
				sb.append(QuantityFormatter.quantityToStackSize(slot.getCost())).append(" gp");
				if (slot.getShortfall() > 0)
				{
					sb.append(" - need ")
						.append(QuantityFormatter.quantityToStackSize(slot.getShortfall()))
						.append(" more");
				}
				sb.append('\n');
			}
			sb.append(slot.getCraftNote());
			sb.append(slot.getOwned() != null
				? "\nYou have " + slot.getOwned().getName()
				: "\nNothing owned in this slot");
			return sb.toString();
		}

		sb.append(slot.getOwned() != null
			? "You have " + slot.getOwned().getName()
			: "Not owned");

		if (slot.getLevelShortfall() != null)
		{
			sb.append("\nNeeds ").append(slot.getLevelShortfall()).append(" to equip");
			return sb.toString();
		}

		if (slot.isTargetOffGe() && slot.getTarget() != null)
		{
			// Infernal cape, Dizana's quiver, Avernic defender and the like -
			// still the upgrade, just earned rather than bought.
			sb.append("\nNext upgrade: ").append(slot.getTarget().getName())
				.append(" - earned from content, not on the GE");
		}

		appendTrainingTip(sb, slot);

		return sb.toString();
	}

	/**
	 * Shown for content you cannot bring equipment into, where any ranking would
	 * be meaningless rather than merely imprecise.
	 */
	public void showGearNotApplicable(CombatStyle style, String reason)
	{
		final StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}

		tab.grid.showEmpty();
		tab.detail.setVisible(false);
		tab.summary.removeAll();
		tab.buySection.removeAll();
		tab.bisSection.removeAll();

		tab.bisSection.add(sectionHeader("Gear does not apply here"));
		tab.bisSection.add(noteRow("Nothing to buy", reason, ColorScheme.BRAND_ORANGE));

		tab.root.revalidate();
		tab.root.repaint();
	}

	/**
	 * Fallback view for bosses with no wiki table bundled yet.
	 */
	public void showComputed(CombatStyle style, UpgradeFinder.Analysis analysis,
							 DamageType usedType, boolean hasBisData)
	{
		final StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}

		tab.grid.update(analysis.getCurrentBest());

		tab.summary.removeAll();
		addStat(tab.summary, "DPS", String.format("%.2f", analysis.getCurrentDps()));
		addStat(tab.summary, "Max hit", String.valueOf(analysis.getMaxHit()));
		addStat(tab.summary, "Accuracy", String.format("%.1f%%", analysis.getAccuracy() * 100));
		addStat(tab.summary, "Attack type", usedType.getDisplayName());

		tab.buySection.removeAll();

		if (!hasBisData)
		{
			tab.bisSection.removeAll();
			tab.bisSection.add(noteRow("No wiki setup bundled yet",
				"The list below is estimated from equipment stats alone. It cannot account "
					+ "for passives, special attacks or set bonuses, so treat it as a hint "
					+ "rather than best in slot.", MISSING));

			tab.buySection.add(sectionHeader("Estimated upgrades"));
			fillSection(tab.buySection, analysis.getAffordable(), usedType, false,
				"Nothing you can afford beats what you already own.");

			tab.buySection.add(sectionHeader("Save up for"));
			fillSection(tab.buySection, analysis.getAspirational(), usedType, true,
				"No bigger upgrades found.");
		}

		tab.root.revalidate();
		tab.root.repaint();
	}

	private void fillSection(JPanel section, List<UpgradeSuggestion> suggestions,
							 DamageType type, boolean showShortfall, String emptyText)
	{
		if (suggestions.isEmpty())
		{
			section.add(hint(emptyText));
			return;
		}

		for (UpgradeSuggestion suggestion : suggestions)
		{
			section.add(suggestionRow(suggestion, type, showShortfall));
		}
	}

	private JPanel suggestionRow(UpgradeSuggestion suggestion, DamageType type,
								 boolean showShortfall)
	{
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(0, 0, 4, 0),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		row.setMinimumSize(new Dimension(1, 1));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ICON_WIDTH, 36));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(suggestion.getItem().getId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JTextArea name =
			PanelText.body(suggestion.getItem().getName(), NAME_FONT, Color.WHITE);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		text.add(PanelText.body(String.format("+%.2f dps  (+%.1f%%)",
			suggestion.getDpsGain(), suggestion.getPercentGain()), DETAIL_FONT, OWNED));

		text.add(PanelText.body(showShortfall
			? "need " + QuantityFormatter.quantityToStackSize(suggestion.getShortfall()) + " more"
			: QuantityFormatter.quantityToStackSize(suggestion.getCost()) + " gp",
			DETAIL_FONT, showShortfall ? MISSING : ColorScheme.GRAND_EXCHANGE_PRICE));

		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static void addStat(JPanel parent, String name, String value)
	{
		parent.add(PanelText.body(name, DETAIL_FONT, ColorScheme.LIGHT_GRAY_COLOR));
		parent.add(PanelText.body(value, NAME_FONT, Color.WHITE));
	}

	private static JTextArea hint(String text)
	{
		final JTextArea area = PanelText.body(text, DETAIL_FONT, ColorScheme.LIGHT_GRAY_COLOR);
		area.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}

	/**
	 * Selects the style with the highest DPS and marks it in the tab title, so
	 * a boss like Akkha - whose magic defence is +10 against +120 slash - opens
	 * on Magic rather than on whichever tab happens to be first.
	 */
	public void setBestStyle(CombatStyle best)
	{
		int index = 0;
		for (CombatStyle style : CombatStyle.values())
		{
			final boolean isBest = style == best;
			tabs.setTitleAt(index, style.getDisplayName() + (isBest ? " ★" : ""));
			if (isBest)
			{
				tabs.setSelectedIndex(index);
			}
			index++;
		}
	}
}
