/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * The local player's own combat levels. Used both to gate which items they can
 * equip and to feed the accuracy and max hit rolls.
 */
@Value
public class PlayerLevels
{
	int attack;
	int strength;
	int defence;
	int ranged;
	int magic;

	public static PlayerLevels from(Client client)
	{
		return new PlayerLevels(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.MAGIC));
	}
}
