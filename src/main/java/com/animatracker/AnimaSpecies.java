package com.animatracker;

import net.runelite.api.gameval.ItemID;

public enum AnimaSpecies
{
	NONE(-1),
	IASOR(ItemID.IASOR_SEED),
	KRONOS(ItemID.KRONOS_SEED),
	ATTAS(ItemID.ATTAS_SEED);

	private final int seedItemId;

	AnimaSpecies(int seedItemId)
	{
		this.seedItemId = seedItemId;
	}

	public int getSeedItemId()
	{
		return seedItemId;
	}

	public String getDisplayName()
	{
		switch (this)
		{
			case IASOR:
				return "Iasor";
			case KRONOS:
				return "Kronos";
			case ATTAS:
				return "Attas";
			default:
				return "Anima patch";
		}
	}
}
