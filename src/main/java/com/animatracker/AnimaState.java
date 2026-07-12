package com.animatracker;

import lombok.Value;

@Value
public class AnimaState
{
	public static final AnimaState EMPTY = new AnimaState(AnimaSpecies.NONE, AnimaLifecycle.EMPTY);

	AnimaSpecies species;
	AnimaLifecycle lifecycle;
}
