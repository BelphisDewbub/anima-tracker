package com.animatracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class AnimaOverlay extends Overlay
{
	private static final int FRAME_SIZE = 32;
	private static final int ICON_PADDING = 4;
	private static final int HOVER_HEIGHT = FRAME_SIZE + 20;

	private final Client client;
	private final TooltipManager tooltipManager;
	private final ItemManager itemManager;
	private final AnimaPatchTracker patchTracker;
	private final NearbyPatchLocator patchLocator;
	private final AnimaTrackerConfig config;

	@Inject
	AnimaOverlay(
		Client client,
		TooltipManager tooltipManager,
		ItemManager itemManager,
		AnimaPatchTracker patchTracker,
		NearbyPatchLocator patchLocator,
		AnimaTrackerConfig config)
	{
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.itemManager = itemManager;
		this.patchTracker = patchTracker;
		this.patchLocator = patchLocator;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		WorldPoint patchLocation = patchLocator.findNearest(localPlayer.getWorldLocation(), config.radius());
		if (patchLocation == null)
		{
			return null;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(client, patchLocation);
		if (localPoint == null)
		{
			return null;
		}

		Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());
		if (canvasPoint == null)
		{
			return null;
		}

		int x = canvasPoint.getX() - FRAME_SIZE / 2;
		int y = canvasPoint.getY() - HOVER_HEIGHT;

		AnimaState state = patchTracker.getState();
		AnimaLifecycle displayLifecycle = effectiveLifecycle(state);

		graphics.setColor(backgroundColor(displayLifecycle));
		graphics.fillRoundRect(x, y, FRAME_SIZE, FRAME_SIZE, 8, 8);
		graphics.setColor(Color.BLACK);
		graphics.drawRoundRect(x, y, FRAME_SIZE, FRAME_SIZE, 8, 8);

		drawIcon(graphics, state, x, y);

		Rectangle bounds = new Rectangle(x, y, FRAME_SIZE, FRAME_SIZE);
		Point mousePosition = client.getMouseCanvasPosition();
		if (mousePosition != null && bounds.contains(mousePosition.getX(), mousePosition.getY()))
		{
			tooltipManager.add(new Tooltip(tooltipText(state, displayLifecycle)));
		}

		return null;
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

	private void drawIcon(Graphics2D graphics, AnimaState state, int frameX, int frameY)
	{
		int iconSize = FRAME_SIZE - ICON_PADDING * 2;
		int iconX = frameX + ICON_PADDING;
		int iconY = frameY + ICON_PADDING;

		if (state.getLifecycle() == AnimaLifecycle.EMPTY)
		{
			drawProhibitionGlyph(graphics, iconX, iconY, iconSize);
			return;
		}

		int itemId = state.getLifecycle() == AnimaLifecycle.DEAD ? ItemID.SKULL : state.getSpecies().getSeedItemId();
		BufferedImage image = itemManager.getImage(itemId);
		graphics.drawImage(image, iconX, iconY, iconSize, iconSize, null);
	}

	private static void drawProhibitionGlyph(Graphics2D graphics, int x, int y, int size)
	{
		Stroke previousStroke = graphics.getStroke();
		Color previousColor = graphics.getColor();

		graphics.setColor(Color.WHITE);
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawOval(x, y, size, size);
		graphics.drawLine(x + 2, y + size - 2, x + size - 2, y + 2);

		graphics.setStroke(previousStroke);
		graphics.setColor(previousColor);
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
		if (state.getLifecycle() == AnimaLifecycle.EMPTY)
		{
			return "Anima patch</br>Nothing planted";
		}

		StringBuilder sb = new StringBuilder(state.getSpecies().getDisplayName());

		if (state.getLifecycle() == AnimaLifecycle.DEAD)
		{
			sb.append("</br>Dead - replant to restore the buff");
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
				sb.append("</br>~").append(formatDuration(remaining)).append(" remaining (estimate)");
			}
		});

		return sb.toString();
	}

	private static String formatDuration(Duration duration)
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
