package com.animatracker;

import net.runelite.api.gameval.ItemID;

public enum AnimaSpecies
{
	NONE(-1),
	IASOR(ItemID.ANIMA_IASOR),
	KRONOS(ItemID.ANIMA_KRONOS),
	ATTAS(ItemID.ANIMA_ATTAS);

	private final int plantIconItemId;

	AnimaSpecies(int plantIconItemId)
	{
		this.plantIconItemId = plantIconItemId;
	}

	public int getPlantIconItemId()
	{
		return plantIconItemId;
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
