package com.animatracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("animatracker")
public interface AnimaTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "radius",
		name = "Detection radius",
		description = "How close (in tiles) you need to be to a farming patch for the anima status frame to appear",
		position = 0
	)
	@Range(min = 1, max = 50)
	default int radius()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "warnBeforeMinutes",
		name = "Warn before estimated death",
		description = "Show the withering (yellow) warning this many minutes before the plant is estimated to die",
		position = 1
	)
	default int warnBeforeMinutes()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "assumedLifespanHours",
		name = "Assumed anima lifespan (hours)",
		description = "Assumed total lifespan of an anima plant, used only to estimate time remaining - not the real dying/dead state",
		position = 2
	)
	default int assumedLifespanHours()
	{
		return 84;
	}
}
