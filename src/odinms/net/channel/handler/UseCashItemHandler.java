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

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import odinms.client.ExpTable;
import odinms.client.IItem;
import odinms.client.ISkill;
import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.MapleInventoryType;
import odinms.client.MapleJob;
import odinms.client.MaplePet;
import odinms.client.MapleStat;
import odinms.client.SkillFactory;
import odinms.net.AbstractMaplePacketHandler;
import odinms.server.MapleInventoryManipulator;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;
import odinms.server.MapleItemInformationProvider;
import odinms.server.constants.Items;
import odinms.tools.Pair;

public class UseCashItemHandler extends AbstractMaplePacketHandler {
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UseCashItemHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		c.getSession().write(MaplePacketCreator.enableActions());
		//@SuppressWarnings("unused")
		//byte mode = slea.readByte();
		slea.readByte();
		slea.readByte();
		int itemId = slea.readInt();
		IItem item = c.getPlayer().getInventory(MapleInventoryType.CASH).findById(itemId);
		if (item == null) {
			c.disconnect();
			return;
		}
		try {
			if (Items.Cash.isSPReset(itemId)) {
				MapleCharacter player = c.getPlayer();
				ISkill skillSPTo = SkillFactory.getSkill(slea.readInt());
				ISkill skillSPFrom = SkillFactory.getSkill(slea.readInt());
				int maxlevel = skillSPTo.getMaxLevel();
				int curLevel = player.getSkillLevel(skillSPTo);
				int curLevelSPFrom = player.getSkillLevel(skillSPFrom);

				if ((curLevel + 1 <= maxlevel) && curLevelSPFrom > 0) {
					player.changeSkillLevel(skillSPFrom, curLevelSPFrom - 1, player.getMasterLevel(skillSPFrom));
					player.changeSkillLevel(skillSPTo, curLevel + 1, player.getMasterLevel(skillSPTo));
				}
			} else if (itemId == Items.Cash.APReset) {
				List<Pair<MapleStat, Integer>> statupdate = new ArrayList<Pair<MapleStat, Integer>>(2);
				int APTo = slea.readInt();
				int APFrom = slea.readInt();

				switch (APFrom) {
					case 64: // str
						if (c.getPlayer().getStr() <= 4) {
							return;
						}
						c.getPlayer().setStr(c.getPlayer().getStr() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.STR, c.getPlayer().getStr()));
						break;
					case 128: // dex
						if (c.getPlayer().getDex() <= 4) {
							return;
						}
						c.getPlayer().setDex(c.getPlayer().getDex() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.DEX, c.getPlayer().getDex()));
						break;
					case 256: // int
						if (c.getPlayer().getInt() <= 4) {
							return;
						}
						c.getPlayer().setInt(c.getPlayer().getInt() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.INT, c.getPlayer().getInt()));
						break;
					case 512: // luk
						if (c.getPlayer().getLuk() <= 4) {
							return;
						}
						c.getPlayer().setLuk(c.getPlayer().getLuk() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.LUK, c.getPlayer().getLuk()));
						break;
					case 2048: // HP
						if (c.getPlayer().getHpApUsed() <= 0) {
							return;
						}
						int maxhp = 0;
						if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
							maxhp -= 12;
						} else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
							ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
							int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
							maxhp -= 24;
							maxhp -= improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
						} else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
							maxhp -= 10;
						} else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
							maxhp -= 20;
						} else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
							maxhp -= 20;
						}
						if (maxhp < ((c.getPlayer().getLevel() * 2) + 148)) {
							return;
						}
						c.getPlayer().setMaxHp(maxhp);
						c.getPlayer().setHp(c.getPlayer().getMaxHp());
						c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.HP, c.getPlayer().getMaxHp()));
					case 8192: // MP
						if (c.getPlayer().getHpApUsed() <= 0) {
							return;
						}
						int maxmp = 0;
						if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
							maxmp -= 8;
						} else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
							maxmp -= 4;
						} else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
							ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
							int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
							maxmp -= 20;
							maxmp -= 2 * improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
						} else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
							maxmp -= 12;
						} else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
							maxmp -= 12;
						}
						if (maxmp < ((c.getPlayer().getLevel() * 2) + 148)) {
							return;
						}
						c.getPlayer().setMaxMp(maxmp);
						c.getPlayer().setMp(c.getPlayer().getMaxMp());
						c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() - 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.MP, c.getPlayer().getMaxMp()));
					default:
						c.getSession().write(
								MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
						return;
				}
				switch (APTo) {
					case 64: // str
						if (c.getPlayer().getStr() >= 999) {
							return;
						}
						c.getPlayer().setStr(c.getPlayer().getStr() + 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.STR, c.getPlayer().getStr()));
						break;
					case 128: // dex
						if (c.getPlayer().getDex() >= 999) {
							return;
						}
						c.getPlayer().setDex(c.getPlayer().getDex() + 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.DEX, c.getPlayer().getDex()));
						break;
					case 256: // int
						if (c.getPlayer().getInt() >= 999) {
							return;
						}
						c.getPlayer().setInt(c.getPlayer().getInt() + 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.INT, c.getPlayer().getInt()));
						break;
					case 512: // luk
						if (c.getPlayer().getLuk() >= 999) {
							return;
						}
						c.getPlayer().setLuk(c.getPlayer().getLuk() + 1);
						statupdate.add(new Pair<MapleStat, Integer>(MapleStat.LUK, c.getPlayer().getLuk()));
						break;
					case 2048: // hp
						int maxhp = c.getPlayer().getMaxHp();
						if (maxhp >= 30000) {
							c.getSession().write(
									MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
							return;
						} else {
							if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
								maxhp += rand(8, 12);
							} else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
								ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
								int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
								maxhp += rand(20, 24);
								maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
							} else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
								maxhp += rand(6, 10);
							} else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
								maxhp += rand(16, 20);
							} else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
								maxhp += rand(16, 20);
							}
							maxhp = Math.min(30000, maxhp);
							c.getPlayer().setMaxHp(maxhp);
							c.getPlayer().setHp(c.getPlayer().getMaxHp());
							c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() - 1);
							statupdate.add(new Pair<MapleStat, Integer>(MapleStat.MAXHP, c.getPlayer().getMaxHp()));
							break;
						}
					case 8192: // mp
						int maxmp = c.getPlayer().getMaxMp();
						if (maxmp >= 30000) {
							return;
						} else {
							if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
								maxmp += rand(6, 8);
							} else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
								maxmp += rand(2, 4);
							} else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
								ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
								int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
								maxmp += rand(18, 20);
								maxmp += 2 * improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
							} else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
								maxmp += rand(10, 12);
							} else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
								maxmp += rand(10, 12);
							}
							maxmp = Math.min(30000, maxmp);
							c.getPlayer().setMaxMp(maxmp);
							c.getPlayer().setMp(c.getPlayer().getMaxMp());
							c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() - 1);
							statupdate.add(new Pair<MapleStat, Integer>(MapleStat.MAXMP, c.getPlayer().getMaxMp()));
							break;
						}
					default:
						c.getSession().write(
								MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
						return;
				}
				c.getSession().write(MaplePacketCreator.updatePlayerStats(statupdate, true));
			} else if (itemId == Items.Cash.Megaphone) {
				String message = c.getPlayer().getName() + " : " + slea.readMapleAsciiString();
				c.getChannelServer().broadcastPacket(MaplePacketCreator.getMegaphone(Items.MegaPhoneType.MEGAPHONE, c.getChannel(), message, null, true));
			} else if (itemId == Items.Cash.SuperMegaphone) {
				String message = c.getPlayer().getName() + " : " + slea.readMapleAsciiString();
				boolean showEar = slea.readByte() > 0;
				c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getMegaphone(Items.MegaPhoneType.SUPERMEGAPHONE, c.getChannel(), message, null, showEar).getBytes());
			} else if (itemId == Items.Cash.TripleMegaphone) {
				int numLines = slea.readByte();
                String[] message = {"", "", ""};
                for (int i = 0; i < numLines; i++) {
                    String msg = slea.readMapleAsciiString();
                    message[i] = c.getPlayer().getName() + " : " + msg;
                }
                boolean showEar = slea.readByte() == 1;
                c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getTripleMegaphone(c.getChannel(), message, showEar).getBytes());
			} else if (itemId == Items.Cash.ItemMegaphone) {
				String message = c.getPlayer().getName() + " :  " + slea.readMapleAsciiString();
                boolean showEar = slea.readByte() == 1;
                IItem megaitem = null;
                if (slea.readByte() == 1) {
                    int invtype = slea.readInt();
                    int slotno = slea.readInt();
                    megaitem = c.getPlayer().getInventory(MapleInventoryType.getByType((byte) invtype)).getItem((byte) slotno);
                }
                c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getMegaphone(Items.MegaPhoneType.ITEMMEGAPHONE, c.getChannel(), message, megaitem, showEar).getBytes());
			} else if (Items.Cash.isAvatarMega(itemId)) {
				List<String> lines = new LinkedList<String>();
				for (int i = 0; i < 4; i++) {
					lines.add(slea.readMapleAsciiString());
				}
				c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getAvatarMega(c.getPlayer(), c.getChannel(), itemId, lines).getBytes());
			} else if (itemId / 1000 == 512) {
				c.getPlayer().getMap().startMapEffect(ii.getMsg(itemId).replaceFirst("%s", c.getPlayer().getName()).replaceFirst("%s", slea.readMapleAsciiString()), itemId);
			} else if (Items.Cash.isPetFood(itemId)) {
				for (int i = 0; i < 3; i++) {
					MaplePet pet = c.getPlayer().getPet(i);
					if (pet == null) {
						c.getSession().write(MaplePacketCreator.enableActions());
						return;
					}
					if (pet.canConsume(itemId)) {
						pet.setFullness(100);
						int closeGain = 100 * c.getChannelServer().getPetExpRate();
						if (pet.getCloseness() < 30000) {
							if (pet.getCloseness() + closeGain > 30000) {
								pet.setCloseness(30000);
							} else {
								pet.setCloseness(pet.getCloseness() + closeGain);
							}
							while (pet.getCloseness() >= ExpTable.getClosenessNeededForLevel(pet.getLevel() + 1)) {
								pet.setLevel(pet.getLevel() + 1);
								c.getSession().write(MaplePacketCreator.showOwnPetLevelUp(c.getPlayer().getPetIndex(pet)));
								c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.showPetLevelUp(c.getPlayer(), c.getPlayer().getPetIndex(pet)));
							}
						}
						c.getSession().write(MaplePacketCreator.updatePet(pet, true));
						c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), 0, 1, true), true);
						break;
					}
				}
				c.getSession().write(MaplePacketCreator.enableActions());
				return;
			} else if (itemId == Items.Cash.PetNameTag) {
				MaplePet pet = c.getPlayer().getPet(0);
				if (pet == null) {
					c.getSession().write(MaplePacketCreator.enableActions());
					return;
				}
				String newName = slea.readMapleAsciiString();
				pet.setName(newName);
				c.getSession().write(MaplePacketCreator.updatePet(pet, true));
				c.getSession().write(MaplePacketCreator.enableActions());
				c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.changePetName(c.getPlayer(), newName, 1), true);
			} else if (itemId == Items.Cash.Note) {
				String sendTo = slea.readMapleAsciiString();
				String msg = slea.readMapleAsciiString();
				c.getPlayer().sendNote(sendTo, msg);
			} else if (itemId == Items.Cash.ViciousHammer) {
				/*byte inventory = */slea.readInt();
				byte slot = (byte) slea.readInt();
				IItem toHammer = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(slot);
				ii.hammerEquip(toHammer);
				c.getSession().write(MaplePacketCreator.sendHammerSlot(slot));
				c.getPlayer().setHammerSlot(Byte.valueOf(slot));

			} else {
				return;
			}
			MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
		} catch (RemoteException e) {
			c.getChannelServer().reconnectWorld();
			log.error("REMOTE ERROR", e);
		} catch (SQLException e) {
			log.error("Error saving note", e);
		}
	}

	private static int rand(int lbound, int ubound) {
		return (int) ((Math.random() * (ubound - lbound + 1)) + lbound);
	}
}
