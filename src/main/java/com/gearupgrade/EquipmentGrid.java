/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/**
 * The worn-equipment layout, arranged like the in-game equipment tab so the
 * chosen setup can be read at a glance.
 */
class EquipmentGrid extends JPanel
{
	/**
	 * Three of these plus insets must stay inside the plugin panel width, or
	 * GridBagLayout centres the grid in an oversized container and the right
	 * column falls off the edge.
	 */
	private static final int CELL = 46;

	/** Slot, row, column - mirroring the in-game equipment screen. */
	private static final int[][] LAYOUT = {
		{EquipmentInventorySlot.HEAD.getSlotIdx(), 0, 1},
		{EquipmentInventorySlot.CAPE.getSlotIdx(), 1, 0},
		{EquipmentInventorySlot.AMULET.getSlotIdx(), 1, 1},
		{EquipmentInventorySlot.AMMO.getSlotIdx(), 1, 2},
		{EquipmentInventorySlot.WEAPON.getSlotIdx(), 2, 0},
		{EquipmentInventorySlot.BODY.getSlotIdx(), 2, 1},
		{EquipmentInventorySlot.SHIELD.getSlotIdx(), 2, 2},
		{EquipmentInventorySlot.LEGS.getSlotIdx(), 3, 1},
		{EquipmentInventorySlot.GLOVES.getSlotIdx(), 4, 0},
		{EquipmentInventorySlot.BOOTS.getSlotIdx(), 4, 1},
		{EquipmentInventorySlot.RING.getSlotIdx(), 4, 2},
	};

	private final ItemManager itemManager;

	/** Notified when a slot is clicked, so the panel can show item details. */
	private Consumer<EquipmentItem> onSelect = item -> {
	};

	void setOnSelect(Consumer<EquipmentItem> listener)
	{
		this.onSelect = listener;
	}

	EquipmentGrid(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new GridBagLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 10, 0));
	}

	void update(Loadout loadout)
	{
		removeAll();

		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);

		for (int[] entry : LAYOUT)
		{
			final int slot = entry[0];
			c.gridy = entry[1];
			c.gridx = entry[2];
			add(cell(loadout.get(slot), slotLabel(slot)), c);
		}

		revalidate();
		repaint();
	}

	private static String slotLabel(int slotIdx)
	{
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			if (slot.getSlotIdx() == slotIdx)
			{
				final String name = slot.name();
				return name.charAt(0) + name.substring(1).toLowerCase();
			}
		}
		return "Slot";
	}

	private JLabel cell(EquipmentItem item, String slotName)
	{
		final JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(CELL, CELL));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));

		if (item == null)
		{
			label.setToolTipText(slotName + ": empty");
			return label;
		}

		// Hovering gives the name and what it is worth, so the grid is readable
		// without clicking through to the wiki.
		final String price = item.isUntradeable()
			? "untradeable"
			: QuantityFormatter.quantityToStackSize(item.getPrice()) + " gp";

		label.setToolTipText("<html><b>" + item.getName() + "</b><br>"
			+ slotName + "<br>" + price + "<br><i>click for details</i></html>");

		label.setCursor(new Cursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onSelect.accept(item);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		itemManager.getImage(item.getId()).addTo(label);
		return label;
	}

	/**
	 * Placeholder used before any analysis has run.
	 */
	void showEmpty()
	{
		removeAll();
		final JLabel label = new JLabel("No setup yet", SwingConstants.CENTER);
		label.setForeground(Color.LIGHT_GRAY);
		add(label);
		revalidate();
		repaint();
	}
}
