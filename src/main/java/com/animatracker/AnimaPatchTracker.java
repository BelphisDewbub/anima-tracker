package com.animatracker;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * Decodes the Farming Guild anima patch varbit into an {@link AnimaState}.
 * <p>
 * Value ranges below come from RuneLite core's own {@code PatchImplementation.ANIMA}, which
 * models this same varbit for its farming tracker plugin.
 * <p>
 * Farming "transmit" varbits like this one are only populated by the server while the
 * associated patch is actually loaded nearby - reading it from anywhere else in the world just
 * returns whatever was last left in that slot (often 0/empty), not the patch's real state. So a
 * live read is only trusted while the player is physically in the Farming Guild; the last
 * confirmed reading is otherwise persisted per RuneScape profile and reused, annotated with its
 * age, so the reminder still works while farming elsewhere.
 */
@Singleton
public class AnimaPatchTracker
{
	private static final String CONFIG_GROUP = "animatracker";
	private static final String KEY_SPECIES = "lastSpecies";
	private static final String KEY_LIFECYCLE = "lastLifecycle";
	private static final String KEY_PLANTED_AT = "plantedAt";
	private static final String KEY_CONFIRMED_AT = "confirmedAt";

	/**
	 * The Farming Guild base region plus the neighbouring scene regions RuneLite's own farming
	 * tracker maps to the same patch set (see FarmingWorld's "Farming Guild" region 4922 entry).
	 */
	private static final int[] ANIMA_PATCH_REGIONS = {4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667};

	private final Client client;
	private final ConfigManager configManager;

	private AnimaState state = AnimaState.EMPTY;
	private Instant plantedAt;
	private Instant confirmedAt;

	@Inject
	public AnimaPatchTracker(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	public AnimaState getState()
	{
		return state;
	}

	public Optional<Duration> getElapsedSincePlanted()
	{
		return plantedAt == null ? Optional.empty() : Optional.of(Duration.between(plantedAt, Instant.now()));
	}

	/**
	 * How long ago the current reading was actually confirmed by being near the real patch.
	 * Empty if it's never been confirmed, this session or a past one.
	 */
	public Optional<Duration> getConfirmedAge()
	{
		return confirmedAt == null ? Optional.empty() : Optional.of(Duration.between(confirmedAt, Instant.now()));
	}

	/**
	 * Loads the last persisted reading for the active profile, then does a live read if the
	 * player happens to already be at the Farming Guild. Call after login.
	 */
	public void refresh()
	{
		loadPersisted();
		if (isNearAnimaPatch())
		{
			apply(client.getVarbitValue(VarbitID.FARMING_TRANSMIT_M));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.FARMING_TRANSMIT_M && isNearAnimaPatch())
		{
			apply(event.getValue());
		}
	}

	private boolean isNearAnimaPatch()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return false;
		}

		int regionId = localPlayer.getWorldLocation().getRegionID();
		for (int region : ANIMA_PATCH_REGIONS)
		{
			if (region == regionId)
			{
				return true;
			}
		}
		return false;
	}

	private void loadPersisted()
	{
		String speciesName = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_SPECIES);
		String lifecycleName = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_LIFECYCLE);
		if (speciesName == null || lifecycleName == null)
		{
			return;
		}

		try
		{
			state = new AnimaState(AnimaSpecies.valueOf(speciesName), AnimaLifecycle.valueOf(lifecycleName));

			String plantedAtStr = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_PLANTED_AT);
			plantedAt = plantedAtStr == null ? null : Instant.parse(plantedAtStr);

			String confirmedAtStr = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_CONFIRMED_AT);
			confirmedAt = confirmedAtStr == null ? null : Instant.parse(confirmedAtStr);
		}
		catch (IllegalArgumentException | DateTimeParseException e)
		{
			// corrupted/unrecognised persisted data - keep defaults
		}
	}

	private void apply(int value)
	{
		AnimaState newState = decode(value);

		if (state.getLifecycle() == AnimaLifecycle.EMPTY && newState.getLifecycle() != AnimaLifecycle.EMPTY)
		{
			plantedAt = Instant.now();
		}
		else if (newState.getLifecycle() == AnimaLifecycle.EMPTY)
		{
			plantedAt = null;
		}

		state = newState;
		confirmedAt = Instant.now();
		persist();
	}

	private void persist()
	{
		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_SPECIES, state.getSpecies().name());
		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_LIFECYCLE, state.getLifecycle().name());
		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_CONFIRMED_AT, confirmedAt.toString());

		if (plantedAt == null)
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_PLANTED_AT);
		}
		else
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_PLANTED_AT, plantedAt.toString());
		}
	}

	private static AnimaState decode(int value)
	{
		if (value >= 8 && value <= 16)
		{
			return new AnimaState(AnimaSpecies.ATTAS, lifecycleWithin(value, 15, 16));
		}
		if (value >= 17 && value <= 25)
		{
			return new AnimaState(AnimaSpecies.IASOR, lifecycleWithin(value, 24, 25));
		}
		if (value >= 26 && value <= 34)
		{
			return new AnimaState(AnimaSpecies.KRONOS, lifecycleWithin(value, 33, 34));
		}
		return AnimaState.EMPTY;
	}

	private static AnimaLifecycle lifecycleWithin(int value, int withering, int dead)
	{
		if (value == dead)
		{
			return AnimaLifecycle.DEAD;
		}
		if (value == withering)
		{
			return AnimaLifecycle.WITHERING;
		}
		return AnimaLifecycle.ALIVE;
	}
}
