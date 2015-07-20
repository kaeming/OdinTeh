/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package odinms.net.channel.handler;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import odinms.client.ISkill;
import odinms.client.MapleBuffStat;
import odinms.client.MapleCharacter;
import odinms.client.MapleJob;
import odinms.client.SkillFactory;
import odinms.server.constants.Skills;
import odinms.client.anticheat.CheatingOffense;
import odinms.client.status.MonsterStatus;
import odinms.client.status.MonsterStatusEffect;
import odinms.net.AbstractMaplePacketHandler;
import odinms.server.AutobanManager;
import odinms.server.MapleStatEffect;
import odinms.server.TimerManager;
import odinms.server.life.Element;
import odinms.server.life.ElementalEffectiveness;
import odinms.server.life.MapleMonster;
import odinms.server.maps.MapleMap;
import odinms.server.maps.MapleMapItem;
import odinms.server.maps.MapleMapObject;
import odinms.server.maps.MapleMapObjectType;
import odinms.tools.MaplePacketCreator;
import odinms.tools.Pair;
import odinms.tools.data.input.LittleEndianAccessor;

public abstract class AbstractDealDamageHandler extends AbstractMaplePacketHandler {
	// private static Logger log = LoggerFactory.getLogger(AbstractDealDamageHandler.class);
	public class AttackInfo {
		public int numAttacked, numDamage, numAttackedAndDamage;
		public int skill, stance, direction, charge, pos;
		public List<Pair<Integer, List<Integer>>> allDamage;
		public boolean isHH = false;
		public int speed = 4;

		private MapleStatEffect getAttackEffect(MapleCharacter chr, ISkill theSkill) {
			ISkill mySkill = theSkill;
			if (mySkill == null) {
				mySkill = SkillFactory.getSkill(skill);
			}
			int skillLevel = chr.getSkillLevel(mySkill);
			if (skillLevel == 0) {
				return null;
			}
			return mySkill.getEffect(skillLevel);
		}

		public MapleStatEffect getAttackEffect(MapleCharacter chr) {
			return getAttackEffect(chr, null);
		}
	}

	protected void applyAttack(AttackInfo attack, MapleCharacter player, int maxDamagePerMonster, int attackCount) {
		player.getCheatTracker().resetHPRegen();
		player.getCheatTracker().checkAttack(attack.skill);
		ISkill theSkill = null;
		MapleStatEffect attackEffect = null;
		if (attack.skill != 0) {
			theSkill = SkillFactory.getSkill(attack.skill);
			attackEffect = attack.getAttackEffect(player, theSkill);
			if (attackEffect == null) {
				AutobanManager.getInstance().autoban(player.getClient(), "Using a skill he doesn't have (" + attack.skill + ")");
			}
			if (attack.skill != Skills.Cleric.Heal && attack.skill != Skills.Marauder.EnergyCharge && attack.skill != Skills.ThunderBreaker2.EnergyCharge) {
				if (player.isAlive()) {
					attackEffect.applyTo(player);
				} else {
					player.getClient().getSession().write(MaplePacketCreator.enableActions());
				}
			}
		}
		if (!player.isAlive()) {
			player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
			return;
		}

		if (attackCount != attack.numDamage && attack.skill != Skills.ChiefBandit.MesoExplosion && attack.numDamage != attackCount * 2) {
			player.getCheatTracker().registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT, attack.numDamage + "/" + attackCount);
		}
		int totDamage = 0;
		MapleMap map = player.getMap();
		synchronized (map) {
			if (attack.skill == Skills.ChiefBandit.MesoExplosion) {

				for (Pair<Integer, List<Integer>> oned : attack.allDamage) {
					MapleMapObject mapobject = map.getMapObject(oned.getLeft().intValue());

					if (mapobject != null && mapobject.getType() == MapleMapObjectType.ITEM) {
						MapleMapItem mapitem = (MapleMapItem) mapobject;
						if (mapitem.getMeso() > 0) {
							synchronized (mapitem) {
								if (mapitem.isPickedUp()) {
									return;
								}
								map.removeMapObject(mapitem);
								map.broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 4, 0), mapitem.getPosition());
								mapitem.setPickedUp(true);
							}
						} else if (mapitem.getMeso() == 0) {
							player.getCheatTracker().registerOffense(CheatingOffense.ETC_EXPLOSION);
							return;
						}
					} else if (mapobject != null && mapobject.getType() != MapleMapObjectType.MONSTER) {
						player.getCheatTracker().registerOffense(CheatingOffense.EXPLODING_NONEXISTANT);
						return; // etc explosion, exploding nonexistant things, etc.

					}
				}
			}

			for (Pair<Integer, List<Integer>> oned : attack.allDamage) {
				MapleMonster monster = map.getMonsterByOid(oned.getLeft().intValue());

				if (monster != null) {
					int totDamageToOneMonster = 0;
					for (Integer eachd : oned.getRight()) {
						totDamageToOneMonster += eachd.intValue();
					}
					totDamage += totDamageToOneMonster;

					Point playerPos = player.getPosition();
					if (totDamageToOneMonster > attack.numDamage + 1) {
						int dmgCheck = player.getCheatTracker().checkDamage(totDamageToOneMonster);
						if (dmgCheck > 5 && totDamageToOneMonster < 99999) {
							player.getCheatTracker().registerOffense(CheatingOffense.SAME_DAMAGE, dmgCheck + " times: " + totDamageToOneMonster);
						}
					}
					checkHighDamage(player, monster, attack, theSkill, attackEffect, totDamageToOneMonster, maxDamagePerMonster);
					double distance = playerPos.distanceSq(monster.getPosition());
					if (distance > 400000.0) {
						player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER, Double.toString(Math.sqrt(distance)));
					}
					if (!monster.isControllerHasAggro()) {
						if (monster.getController() == player) {
							monster.setControllerHasAggro(true);
						} else {
							monster.switchController(player, true);
						}
					}
					// only ds, sb, assaulter, normal (does it work for thieves, bs, or assasinate?)
					if ((attack.skill == Skills.Rogue.DoubleStab || attack.skill == Skills.Bandit.SavageBlow || attack.skill == 0 || attack.skill == Skills.ChiefBandit.Assaulter || attack.skill == Skills.Shadower.BoomerangStep) && player.getBuffedValue(MapleBuffStat.PICKPOCKET) != null) {
						handlePickPocket(player, monster, oned);
					}

					if (attack.skill == Skills.Assassin.Drain) { // drain
						ISkill drain = SkillFactory.getSkill(4101005);
						int gainhp = (int) ((double) totDamageToOneMonster * (double) drain.getEffect(player.getSkillLevel(drain)).getX() / 100.0);
						gainhp = Math.min(monster.getMaxHp(), Math.min(gainhp, player.getMaxHp() / 2));
						player.addHP(gainhp);
					}

					if (player.getBuffedValue(MapleBuffStat.HAMSTRING) != null) {
						ISkill hamstring = SkillFactory.getSkill(Skills.Bowmaster.Hamstring);
						if (hamstring.getEffect(player.getSkillLevel(hamstring)).makeChanceResult()) {
							MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.SPEED, hamstring.getEffect(player.getSkillLevel(hamstring)).getX()), hamstring, false);
							monster.applyStatus(player, monsterStatusEffect, false, hamstring.getEffect(player.getSkillLevel(hamstring)).getY() * 1000);
						}
					}

					if (player.getBuffedValue(MapleBuffStat.BLIND) != null) {
						ISkill blind = SkillFactory.getSkill(Skills.Marksman.Blind);
						if (blind.getEffect(player.getSkillLevel(blind)).makeChanceResult()) {
							MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.ACC, blind.getEffect(player.getSkillLevel(blind)).getX()), blind, false);
							monster.applyStatus(player, monsterStatusEffect, false, blind.getEffect(player.getSkillLevel(blind)).getY() * 1000);
						}
					}

					if (player.getJob().isA(MapleJob.WHITEKNIGHT)) {
						int[] charges = new int[]{Skills.WhiteKnight.BlizzardChargeBW, Skills.WhiteKnight.IceChargeSword};
						for (int charge : charges) {
							ISkill chargeSkill = SkillFactory.getSkill(charge);

							if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, chargeSkill)) {
								final ElementalEffectiveness iceEffectiveness = monster.getEffectiveness(Element.ICE);
								if (totDamageToOneMonster > 0 && iceEffectiveness == ElementalEffectiveness.NORMAL || iceEffectiveness == ElementalEffectiveness.WEAK) {
									MapleStatEffect chargeEffect = chargeSkill.getEffect(player.getSkillLevel(chargeSkill));
									MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.FREEZE, 1), chargeSkill, false);
									monster.applyStatus(player, monsterStatusEffect, false, chargeEffect.getY() * 2000);
								}
								break;
							}
						}
					}

					ISkill venomNL = SkillFactory.getSkill(Skills.NightLord.VenomousStar);
					ISkill venomShadower = SkillFactory.getSkill(Skills.Shadower.VenomousStab);
					if (player.getSkillLevel(venomNL) > 0) {
						MapleStatEffect venomEffect = venomNL.getEffect(player.getSkillLevel(venomNL));
						for (int i = 0; i < attackCount; i++) {
							if (venomEffect.makeChanceResult() == true) {
								if (monster.getVenomMulti() < 3) {
									monster.setVenomMulti((monster.getVenomMulti() + 1));
									MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), venomNL, false);
									monster.applyStatus(player, monsterStatusEffect, false, venomEffect.getDuration(), true);
								}
							}
						}
					} else if (player.getSkillLevel(venomShadower) > 0) {
						MapleStatEffect venomEffect = venomShadower.getEffect(player.getSkillLevel(venomShadower));
						for (int i = 0; i < attackCount; i++) {
							if (venomEffect.makeChanceResult() == true) {
								if (monster.getVenomMulti() < 3) {
									monster.setVenomMulti((monster.getVenomMulti() + 1));
									MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), venomShadower, false);
									monster.applyStatus(player, monsterStatusEffect, false, venomEffect.getDuration(), true);
								}
							}
						}
					}

					if (totDamageToOneMonster > 0 && attackEffect != null && attackEffect.getMonsterStati().size() > 0) {
						if (attackEffect.makeChanceResult()) {
							MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(attackEffect.getMonsterStati(), theSkill, false);
							monster.applyStatus(player, monsterStatusEffect, attackEffect.isPoison(), attackEffect.getDuration());
						}
					}

					if (attack.isHH && !monster.isBoss()) {
						map.damageMonster(player, monster, monster.getHp() - 1);
					} else if (attack.isHH && monster.isBoss()) {
						map.damageMonster(player, monster, 199999);
					} else {
						map.damageMonster(player, monster, totDamageToOneMonster);
					}
				}
			}
		}

		if (totDamage > 1) {
			player.getCheatTracker().setAttacksWithoutHit(player.getCheatTracker().getAttacksWithoutHit() + 1);
			final int offenseLimit;
			if (attack.skill != Skills.Bowmaster.Hurricane && attack.skill != Skills.WindArcher3.Hurricane) {
				offenseLimit = 100;
			} else {
				offenseLimit = 300;
			}
			if (player.getCheatTracker().getAttacksWithoutHit() > offenseLimit) {
				player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT, Integer.toString(player.getCheatTracker().getAttacksWithoutHit()));
			}
		}

		if (player.hasEnergyCharge()) {
			player.increaseEnergyCharge(attack.numAttacked);
		}
	}

	private void handlePickPocket(MapleCharacter player, MapleMonster monster, Pair<Integer, List<Integer>> oned) {
		ISkill pickpocket = SkillFactory.getSkill(Skills.ChiefBandit.Pickpocket);
		int delay = 0;
		int maxmeso = player.getBuffedValue(MapleBuffStat.PICKPOCKET).intValue();
		int reqdamage = 20000;
		Point monsterPosition = monster.getPosition();

		for (Integer eachd : oned.getRight()) {
			if (pickpocket.getEffect(player.getSkillLevel(pickpocket)).makeChanceResult()) {
				double perc = (double) eachd / (double) reqdamage;

				final int todrop = Math.min((int) Math.max(perc * (double) maxmeso, (double) 1), maxmeso);
				final MapleMap tdmap = player.getMap();
				final Point tdpos = new Point((int) (monsterPosition.getX() + (Math.random() * 100) - 50), (int) (monsterPosition.getY()));
				final MapleMonster tdmob = monster;
				final MapleCharacter tdchar = player;

				TimerManager.getInstance().schedule(new Runnable() {
					public void run() {
						tdmap.spawnMesoDrop(todrop, todrop, tdpos, tdmob, tdchar, false);
					}
				}, delay);

				delay += 200;
			}
		}
	}

	private void checkHighDamage(MapleCharacter player, MapleMonster monster, AttackInfo attack, ISkill theSkill, MapleStatEffect attackEffect, int damageToMonster, int maximumDamageToMonster) {
		int elementalMaxDamagePerMonster;
		Element element = Element.NEUTRAL;
		if (theSkill != null) {
			element = theSkill.getElement();
			int skillId = theSkill.getId();
			if (skillId == Skills.Marksman.Snipe || skillId == Skills.Marksman.PiercingArrow) {
				maximumDamageToMonster = 199999;
			} else if (skillId == Skills.Shadower.Assassinate) {
				maximumDamageToMonster = 600000;
			}
		}
		if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null) {
			int chargeSkillId = player.getBuffSource(MapleBuffStat.WK_CHARGE);
			switch (chargeSkillId) {
				case Skills.WhiteKnight.FireChargeSword:
				case Skills.WhiteKnight.FlameChargeBW:
					element = Element.FIRE;
					break;
				case Skills.WhiteKnight.BlizzardChargeBW:
				case Skills.WhiteKnight.IceChargeSword:
					element = Element.ICE;
					break;
				case Skills.WhiteKnight.LightningChargeBW:
				case Skills.WhiteKnight.ThunderChargeSword:
					element = Element.LIGHTING;
					break;
				case Skills.Paladin.DivineChargeBW:
				case Skills.Paladin.HolyChargeSword:
				case Skills.DawnWarrior3.SoulCharge:
					element = Element.HOLY;
					break;
			}
			ISkill chargeSkill = SkillFactory.getSkill(chargeSkillId);
			maximumDamageToMonster *= chargeSkill.getEffect(player.getSkillLevel(chargeSkill)).getDamage() / 100.0;
		}

		if (element != Element.NEUTRAL) {
			double elementalEffect;
			if (attack.skill == Skills.Ranger.Inferno || attack.skill == Skills.Sniper.Blizzard) {
				elementalEffect = attackEffect.getX() / 200.0;
			} else {
				elementalEffect = 0.5;
			}
			switch (monster.getEffectiveness(element)) {
				case IMMUNE:
					elementalMaxDamagePerMonster = 1;
					break;
				case NORMAL:
					elementalMaxDamagePerMonster = maximumDamageToMonster;
					break;
				case WEAK:
					elementalMaxDamagePerMonster = (int) (maximumDamageToMonster * (1.0 + elementalEffect));
					break;
				case STRONG:
					elementalMaxDamagePerMonster = (int) (maximumDamageToMonster * (1.0 - elementalEffect));
					break;
				default:
					throw new RuntimeException("Unknown enum constant");
			}
		} else {
			elementalMaxDamagePerMonster = maximumDamageToMonster;
		}

		if (damageToMonster > elementalMaxDamagePerMonster) {
			player.getCheatTracker().registerOffense(CheatingOffense.HIGH_DAMAGE);
			if (damageToMonster > elementalMaxDamagePerMonster * 3) {
				AutobanManager.getInstance().autoban(player.getClient(), damageToMonster + " damage (level: " + player.getLevel() + " watk: " + player.getTotalWatk() + " skill: " + attack.skill + ", monster: " + monster.getId() + " assumed max damage: " + elementalMaxDamagePerMonster + ")");
			}
		}
	}

	public AttackInfo parseRanged(MapleCharacter chr, LittleEndianAccessor lea) {
		AttackInfo ret = new AttackInfo();
		lea.readByte();
		ret.numAttackedAndDamage = lea.readByte();
		ret.numAttacked = (ret.numAttackedAndDamage >>> 4) & 0xF;
		ret.numDamage = ret.numAttackedAndDamage & 0xF;
		ret.allDamage = new ArrayList<Pair<Integer, List<Integer>>>();
		ret.skill = lea.readInt();
		lea.readInt(); // Mob's .img size
		lea.readInt();
		switch (ret.skill) {
			case Skills.Bowmaster.Hurricane:
			case Skills.Marksman.PiercingArrow:
			case Skills.Corsair.RapidFire:
			case Skills.WindArcher3.Hurricane:
				lea.readInt();
				break;
		}
		lea.readByte(); // Projectile that is thrown
		ret.stance = lea.readByte();
		lea.readByte(); // Weapon subclass
		ret.speed = lea.readByte();
		lea.readInt();
		lea.readShort(); // Slot
		lea.readShort(); // CS Star
		lea.readByte();
		for (int i = 0; i < ret.numAttacked; i++) {
			int mobId = lea.readInt();
			lea.skip(14);
			List<Integer> allDamageNumbers = new ArrayList<Integer>();
			for (int j = 0; j < ret.numDamage; j++) {
				allDamageNumbers.add(Integer.valueOf(lea.readInt()));
			}
			ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(mobId), allDamageNumbers));
			lea.readInt();
		}
		lea.readInt();
		ret.pos = lea.readInt();
		return ret;
	}

	public AttackInfo parseDamage(MapleCharacter chr, LittleEndianAccessor lea, boolean ranged) {
		AttackInfo ret = new AttackInfo();

		lea.readByte();
		ret.numAttackedAndDamage = lea.readByte();
		ret.numAttacked = (ret.numAttackedAndDamage >>> 4) & 0xF;
		ret.numDamage = ret.numAttackedAndDamage & 0xF;
		ret.allDamage = new ArrayList<Pair<Integer, List<Integer>>>();
		ret.skill = lea.readInt();
		lea.readInt();
		lea.readInt();
		lea.readByte();
		ret.stance = lea.readByte();

		switch (ret.skill) {
			case Skills.FPArchMage.BigBang:
			case Skills.ILArchMage.BigBang:
			case Skills.Bishop.BigBang:
			case Skills.Gunslinger.Grenade:
			case Skills.Brawler.CorkscrewBlow:
			case Skills.NightWalker3.PoisonBomb:
			case Skills.ThunderBreaker2.CorkscrewBlow:
				ret.charge = lea.readInt();
				break;
			case Skills.Bowmaster.Hurricane:
			case Skills.Marksman.PiercingArrow:
			case Skills.Corsair.RapidFire:
			case Skills.WindArcher3.Hurricane:
				lea.readInt();
			default:
				ret.charge = 0;
				break;
		}

		if (ret.skill == Skills.Paladin.HeavensHammer) {
			ret.isHH = true;
		}

		if (ret.skill == Skills.ChiefBandit.MesoExplosion) {
			return parseMesoExplosion(lea, ret);
		}

		if (ranged && ret.skill != Skills.DawnWarrior2.SoulBlade && ret.skill != Skills.ThunderBreaker3.SharkWave) {
			lea.readByte();
			ret.speed = lea.readByte();
			lea.readByte();
			ret.direction = lea.readByte();
			lea.skip(7);
			if (ret.skill == Skills.Bowmaster.Hurricane || ret.skill == Skills.Marksman.PiercingArrow || ret.skill == Skills.Corsair.RapidFire || ret.skill == Skills.WindArcher3.Hurricane) {
				lea.skip(4);
			}
		} else if (ret.skill == Skills.DawnWarrior2.SoulBlade || ret.skill == Skills.ThunderBreaker3.SharkWave) {
			lea.readByte();
			ret.speed = lea.readByte();
			lea.skip(3);
			ret.direction = lea.readByte();
			lea.skip(5);
		} else {
			lea.readByte();
			ret.speed = lea.readByte();
			lea.skip(3);
			lea.readByte();
		}

		for (int i = 0; i < ret.numAttacked; i++) {
			int oid = lea.readInt();
			lea.readByte();
			lea.skip(13);
			List<Integer> allDamageNumbers = new ArrayList<Integer>();
			for (int j = 0; j < ret.numDamage; j++) {
				int damage = lea.readInt();
				MapleStatEffect effect = null;
				if (ret.skill != 0) {
					effect = SkillFactory.getSkill(ret.skill).getEffect(chr.getSkillLevel(SkillFactory.getSkill(ret.skill)));
				}
				if (damage != 0 && effect != null && effect.getFixedDamage() != 0) {
					damage = effect.getFixedDamage();
				}
				allDamageNumbers.add(Integer.valueOf(damage));
			}
			lea.readInt();
			ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(oid), allDamageNumbers));
		}
		if (ranged) {
			lea.readInt();
		}
		ret.pos = lea.readInt();

		return ret;
	}

	public AttackInfo parseMesoExplosion(LittleEndianAccessor lea, AttackInfo ret) {
		if (ret.numAttackedAndDamage == 0) {
			lea.skip(10);
			int bullets = lea.readByte();
			for (int j = 0; j < bullets; j++) {
				int mesoid = lea.readInt();
				lea.skip(1);
				ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(mesoid), null));
			}
			return ret;
		} else {
			lea.skip(6);
		}
		for (int i = 0; i < ret.numAttacked; i++) {
			int oid = lea.readInt();
			lea.skip(12);
			int bullets = lea.readByte();
			List<Integer> allDamageNumbers = new ArrayList<Integer>();
			for (int j = 0; j < bullets; j++) {
				int damage = lea.readInt();
				allDamageNumbers.add(Integer.valueOf(damage));
			}
			ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(oid), allDamageNumbers));
			lea.skip(4);
		}
		lea.skip(4);
		int bullets = lea.readByte();
		for (int j = 0; j < bullets; j++) {
			int mesoid = lea.readInt();
			lea.skip(1); // 0 = not hit, 1 = hit 1, 3 = hit 2, 3F = hit 6 ????
			ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(mesoid), null));
		}
		return ret;
	}
}
