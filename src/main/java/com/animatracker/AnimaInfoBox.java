package com.animatracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * A draggable buff-bar-style indicator (same mechanism RuneLite uses for potion/prayer timers)
 * showing the Farming Guild anima patch's status, visible whenever the player is near any
 * farming patch. Unlike a world overlay, the user can drag this to wherever they want on screen
 * and RuneLite remembers the position.
 */
class AnimaInfoBox extends InfoBox
{
	private static final int FRAME_SIZE = 32;
	private static final int ICON_PADDING = 4;

	private final Client client;
	private final ItemManager itemManager;
	private final AnimaPatchTracker patchTracker;
	private final NearbyPatchLocator patchLocator;
	private final AnimaTrackerConfig config;

	private AnimaSpecies lastRenderedSpecies;
	private AnimaLifecycle lastRenderedLifecycle;

	AnimaInfoBox(
		AnimaTrackerPlugin plugin,
		Client client,
		ItemManager itemManager,
		AnimaPatchTracker patchTracker,
		NearbyPatchLocator patchLocator,
		AnimaTrackerConfig config)
	{
		super(null, plugin);
		this.client = client;
		this.itemManager = itemManager;
		this.patchTracker = patchTracker;
		this.patchLocator = patchLocator;
		this.config = config;
		updateAppearance(AnimaSpecies.NONE, AnimaLifecycle.EMPTY);
	}

	@Override
	public boolean render()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || patchLocator.findNearest(localPlayer.getWorldLocation(), config.radius()) == null)
		{
			return false;
		}

		AnimaState state = patchTracker.getState();
		AnimaLifecycle displayLifecycle = effectiveLifecycle(state);

		if (state.getSpecies() != lastRenderedSpecies || displayLifecycle != lastRenderedLifecycle)
		{
			updateAppearance(state.getSpecies(), displayLifecycle);
		}

		setTooltip(tooltipText(state, displayLifecycle));
		return true;
	}

	@Override
	public String getText()
	{
		AnimaState state = patchTracker.getState();
		if (state.getLifecycle() == AnimaLifecycle.EMPTY || state.getLifecycle() == AnimaLifecycle.DEAD)
		{
			return "";
		}

		return estimatedRemaining()
			.filter(remaining -> !remaining.isNegative())
			.map(AnimaInfoBox::formatShort)
			.orElse("");
	}

	@Override
	public Color getTextColor()
	{
		return Color.WHITE;
	}

	private AnimaLifecycle effectiveLifecycle(AnimaState state)
	{
		if (state.getLifecycle() != AnimaLifecycle.ALIVE)
		{
			return state.getLifecycle();
		}

		Optional<Duration> remaining = estimatedRemaining();
		if (remaining.isPresent() && remaining.get().toMinutes() <= config.warnBeforeMinutes())
		{
			return AnimaLifecycle.WITHERING;
		}

		return AnimaLifecycle.ALIVE;
	}

	private Optional<Duration> estimatedRemaining()
	{
		return patchTracker.getElapsedSincePlanted()
			.map(elapsed -> Duration.ofHours(config.assumedLifespanHours()).minus(elapsed));
	}

	private void updateAppearance(AnimaSpecies species, AnimaLifecycle lifecycle)
	{
		BufferedImage image = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(backgroundColor(lifecycle));
		graphics.fillRoundRect(0, 0, FRAME_SIZE, FRAME_SIZE, 8, 8);
		graphics.setColor(Color.BLACK);
		graphics.drawRoundRect(0, 0, FRAME_SIZE - 1, FRAME_SIZE - 1, 8, 8);

		drawIcon(graphics, species, lifecycle);
		graphics.dispose();

		setImage(image);
		lastRenderedSpecies = species;
		lastRenderedLifecycle = lifecycle;
	}

	private void drawIcon(Graphics2D graphics, AnimaSpecies species, AnimaLifecycle lifecycle)
	{
		int iconSize = FRAME_SIZE - ICON_PADDING * 2;

		if (lifecycle == AnimaLifecycle.EMPTY)
		{
			drawProhibitionGlyph(graphics, ICON_PADDING, ICON_PADDING, iconSize);
			return;
		}

		int itemId = lifecycle == AnimaLifecycle.DEAD ? ItemID.SKULL : species.getPlantIconItemId();
		BufferedImage icon = itemManager.getImage(itemId);
		graphics.drawImage(icon, ICON_PADDING, ICON_PADDING, iconSize, iconSize, null);
	}

	private static void drawProhibitionGlyph(Graphics2D graphics, int x, int y, int size)
	{
		graphics.setColor(Color.WHITE);
		graphics.setStroke(new java.awt.BasicStroke(2f));
		graphics.drawOval(x, y, size, size);
		graphics.drawLine(x + 2, y + size - 2, x + size - 2, y + 2);
	}

	private static Color backgroundColor(AnimaLifecycle lifecycle)
	{
		switch (lifecycle)
		{
			case ALIVE:
				return new Color(46, 160, 67);
			case WITHERING:
				return new Color(219, 179, 32);
			case DEAD:
				return new Color(200, 60, 60);
			case EMPTY:
			default:
				return new Color(120, 120, 120);
		}
	}

	private String tooltipText(AnimaState state, AnimaLifecycle displayLifecycle)
	{
		Optional<Duration> confirmedAge = patchTracker.getConfirmedAge();

		if (state.getLifecycle() == AnimaLifecycle.EMPTY)
		{
			if (!confirmedAge.isPresent())
			{
				return "Anima patch</br>Unknown - visit the Farming Guild to check";
			}
			return "Anima patch</br>Nothing planted" + confirmedSuffix(confirmedAge);
		}

		StringBuilder sb = new StringBuilder(state.getSpecies().getDisplayName());

		if (state.getLifecycle() == AnimaLifecycle.DEAD)
		{
			sb.append("</br>Dead - replant to restore the buff");
			sb.append(confirmedSuffix(confirmedAge));
			return sb.toString();
		}

		if (state.getLifecycle() == AnimaLifecycle.WITHERING)
		{
			sb.append("</br>Withering");
		}
		else
		{
			sb.append(displayLifecycle == AnimaLifecycle.WITHERING ? "</br>Healthy (dying soon)" : "</br>Healthy");
		}

		estimatedRemaining().ifPresent(remaining ->
		{
			if (!remaining.isNegative())
			{
				sb.append("</br>~").append(formatLong(remaining)).append(" remaining (estimate)");
			}
		});

		sb.append(confirmedSuffix(confirmedAge));

		return sb.toString();
	}

	/**
	 * The anima patch's "transmit" varbit only reflects reality while the player is actually near
	 * it, so anywhere else this indicator is showing the last confirmed reading rather than a
	 * live one - make that age visible instead of silently presenting stale data as current.
	 */
	private static String confirmedSuffix(Optional<Duration> confirmedAge)
	{
		return confirmedAge
			.filter(age -> age.toMinutes() >= 1)
			.map(age -> "</br>(as of " + formatShort(age) + " ago)")
			.orElse("");
	}

	private static String formatShort(Duration duration)
	{
		long totalMinutes = duration.toMinutes();
		long days = totalMinutes / (24 * 60);
		if (days > 0)
		{
			return days + "d";
		}
		long hours = totalMinutes / 60;
		if (hours > 0)
		{
			return hours + "h";
		}
		return totalMinutes + "m";
	}

	private static String formatLong(Duration duration)
	{
		long totalMinutes = duration.toMinutes();
		long days = totalMinutes / (24 * 60);
		long hours = (totalMinutes % (24 * 60)) / 60;
		long minutes = totalMinutes % 60;

		StringBuilder sb = new StringBuilder();
		if (days > 0)
		{
			sb.append(days).append("d ");
		}
		if (days > 0 || hours > 0)
		{
			sb.append(hours).append("h ");
		}
		sb.append(minutes).append("m");
		return sb.toString();
	}
}
