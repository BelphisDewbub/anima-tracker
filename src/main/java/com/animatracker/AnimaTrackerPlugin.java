package com.animatracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@PluginDescriptor(
	name = "Anima Tracker",
	description = "Shows the status of your Farming Guild anima patch whenever you're near a farming patch",
	tags = {"farming", "anima", "iasor", "kronos", "attas", "farming guild"}
)
public class AnimaTrackerPlugin extends Plugin
{
	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private AnimaPatchTracker patchTracker;

	@Inject
	private NearbyPatchLocator patchLocator;

	@Inject
	private AnimaTrackerConfig config;

	private AnimaInfoBox animaInfoBox;

	@Override
	protected void startUp()
	{
		eventBus.register(patchTracker);
		eventBus.register(patchLocator);
		animaInfoBox = new AnimaInfoBox(this, client, itemManager, infoBoxManager, patchTracker, patchLocator, config);
		infoBoxManager.addInfoBox(animaInfoBox);
		clientThread.invoke(patchTracker::refresh);
	}

	@Override
	protected void shutDown()
	{
		infoBoxManager.removeInfoBox(animaInfoBox);
		animaInfoBox = null;
		eventBus.unregister(patchLocator);
		eventBus.unregister(patchTracker);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			patchTracker.refresh();
		}
	}

	@Provides
	AnimaTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AnimaTrackerConfig.class);
	}
}
