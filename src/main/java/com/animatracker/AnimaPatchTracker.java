package com.animatracker;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Decodes the Farming Guild anima patch varbit into an {@link AnimaState} and keeps track of
 * roughly how long the current plant has been alive, so we can estimate time-to-death.
 * <p>
 * Value ranges below come from RuneLite core's own {@code PatchImplementation.ANIMA}, which
 * models this same varbit for its farming tracker plugin.
 */
@Singleton
public class AnimaPatchTracker
{
	private final Client client;

	private AnimaState state = AnimaState.EMPTY;
	private Instant plantedAt;

	@Inject
	public AnimaPatchTracker(Client client)
	{
		this.client = client;
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
	 * Reads the current varbit value directly. Call this once after login so state is correct
	 * immediately instead of waiting for the next {@link VarbitChanged}.
	 */
	public void refresh()
	{
		apply(client.getVarbitValue(VarbitID.FARMING_TRANSMIT_M));
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.FARMING_TRANSMIT_M)
		{
			apply(event.getValue());
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
