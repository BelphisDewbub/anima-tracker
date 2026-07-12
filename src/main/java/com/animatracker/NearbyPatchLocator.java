package com.animatracker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks every currently-loaded farming patch tile, so the overlay can anchor near whichever
 * patch the player is closest to. Patches are detected generically via their "Guide" menu
 * action (the same one behind the in-game Farming Guide), rather than an explicit object-id
 * allowlist, so every patch type in the game is picked up without needing to enumerate them.
 */
@Singleton
public class NearbyPatchLocator
{
	private static final String GUIDE_ACTION = "Guide";

	private final Client client;
	private final Map<WorldPoint, GameObject> patchTiles = new HashMap<>();

	@Inject
	public NearbyPatchLocator(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject gameObject = event.getGameObject();
		if (isFarmingPatch(client.getObjectDefinition(gameObject.getId())))
		{
			patchTiles.put(gameObject.getWorldLocation(), gameObject);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		patchTiles.remove(event.getGameObject().getWorldLocation(), event.getGameObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			patchTiles.clear();
		}
	}

	private static boolean isFarmingPatch(ObjectComposition composition)
	{
		if (composition == null)
		{
			return false;
		}

		ObjectComposition impostor = composition.getImpostorIds() != null ? composition.getImpostor() : composition;
		return impostor != null && impostor.getActions() != null && Arrays.asList(impostor.getActions()).contains(GUIDE_ACTION);
	}

	/**
	 * @return the closest tracked patch tile within {@code radiusTiles} of {@code player}, or null.
	 */
	public WorldPoint findNearest(WorldPoint player, int radiusTiles)
	{
		WorldPoint nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (WorldPoint patch : patchTiles.keySet())
		{
			if (patch.getPlane() != player.getPlane())
			{
				continue;
			}

			int distance = patch.distanceTo(player);
			if (distance <= radiusTiles && distance < nearestDistance)
			{
				nearest = patch;
				nearestDistance = distance;
			}
		}

		return nearest;
	}
}
