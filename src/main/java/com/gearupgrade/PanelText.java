/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JTextArea;

/**
 * Wrapping body text for the side panel.
 *
 * <p>Fixed-pixel HTML wrapping was tried twice and clipped both times, because
 * the usable width inside a tab is not worth guessing at. A line-wrapping text
 * area wraps to whatever width the layout gives it instead.
 *
 * <p>The catch is that a plain JTextArea reports its <em>minimum</em> and
 * preferred width as the full unwrapped line. Inside a fixed-width plugin
 * panel that propagates upwards and widens RuneLite's whole sidebar, which
 * resizes the game canvas. Reporting a width of 1 for both means this
 * component can never drive the layout - it only ever accepts the width it is
 * given, and reports the height that text needs at that width.
 */
final class PanelText
{
	private PanelText()
	{
	}

	private static class WrappingTextArea extends JTextArea
	{
		WrappingTextArea(String text)
		{
			super(text);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return new Dimension(1, super.getMinimumSize().height);
		}

		@Override
		public Dimension getPreferredSize()
		{
			// Width of 1 so the surrounding layout decides the width; the height
			// still comes from the wrapped text at whatever width was assigned.
			return new Dimension(1, super.getPreferredSize().height);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
		}
	}

	static JTextArea body(String text, Font font, Color colour)
	{
		final JTextArea area = new WrappingTextArea(text == null ? "" : text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setBorder(null);
		area.setFont(font);
		area.setForeground(colour);

		// BoxLayout centres components by default, which shunts these sideways
		// against left-aligned siblings and makes text look clipped.
		area.setAlignmentX(Component.LEFT_ALIGNMENT);

		return area;
	}
}
