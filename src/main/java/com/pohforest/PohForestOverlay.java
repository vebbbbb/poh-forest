package com.pohforest;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class PohForestOverlay extends OverlayPanel
{
	private final PohForestPlugin plugin;
	private final PohForestConfig config;

	@Inject
	PohForestOverlay(PohForestPlugin plugin, PohForestConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.debugOverlay() || !plugin.isInPoh())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("POH Forest").build());
		line("Preset", String.valueOf(plugin.getActivePreset()));
		line("Objects", plugin.getTreeCount() + " trees + " + plugin.getUndergrowthCount() + " undergrowth / "
			+ config.maxObjects());
		line("Spawn time", plugin.getSpawnMillis() + " ms");
		line("House", plugin.getHouseSummary());
		line("Spacing", plugin.getDensityCurve());
		return super.render(graphics);
	}

	private void line(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}
}
