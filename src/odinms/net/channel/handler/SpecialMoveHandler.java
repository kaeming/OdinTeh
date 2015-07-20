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

import java.util.concurrent.ScheduledFuture;
import odinms.client.ISkill;
import odinms.client.MapleCharacter.CancelCooldownAction;
import odinms.client.MapleClient;
import odinms.client.SkillFactory;
import odinms.client.messages.ServernoticeMapleClientMessageCallback;
import odinms.net.AbstractMaplePacketHandler;
import odinms.server.AutobanManager;
import odinms.server.MapleStatEffect;
import odinms.server.TimerManager;
import odinms.server.constants.Skills;
import odinms.server.life.MapleMonster;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class SpecialMoveHandler extends AbstractMaplePacketHandler {
	// private static Logger log = LoggerFactory.getLogger(SpecialMoveHandler.class);
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// [53 00] [12 62] [AA 01] [6B 6A 23 00] [1E] [BA 00] [97 00] 00
		//first 2 bytes always semi randomly change
		slea.readByte();
		slea.readByte();
		//@SuppressWarnings("unused")
		/*int unk = */		slea.readShort();
		int skillid = slea.readInt();

		if (c.getPlayer().isOnCoolDown(skillid)) {
			c.disconnect(); // Naughty, no hacking. Not Made this autoban because of lag
			return;
		}

		// seems to be skilllevel for movement skills and -32748 for buffs
		Point pos = null;
		int __skillLevel = slea.readByte();
		int addedInfo = 0;

		ISkill skill = SkillFactory.getSkill(skillid);
		int skillLevel = c.getPlayer().getSkillLevel(skill);
		MapleStatEffect effect = skill.getEffect(skillLevel);
		if (effect.getCooldown() > 0 && skillid != Skills.Corsair.Battleship) {
			c.getSession().write(MaplePacketCreator.skillCooldown(skillid, effect.getCooldown()));
			ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), skillid), effect.getCooldown() * 1000);
			c.getPlayer().addCooldown(skillid, System.currentTimeMillis(), effect.getCooldown() * 1000, timer);
		}

		// [53 00] [90] [16] [E5 00] [E9 1A 11 00] [1E] [03 00 00 00] [67 00 00 00] [01] [69 00 00 00] [01] [6A 00 00 00] [01] [01]
		if (skillid == Skills.Hero.MonsterMagnet || skillid == Skills.DarkKnight.MonsterMagnet || skillid == Skills.Paladin.MonsterMagnet) { // Monster Magnet
			int num = slea.readInt();
			int mobId;
			byte success;
			for (int i = 0; i < num; i++) {
				mobId = slea.readInt();
				success = slea.readByte();
				c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showMagnet(mobId, success), false);
				MapleMonster monster = c.getPlayer().getMap().getMonsterByOid(mobId);
				if (monster != null) {
					monster.switchController(c.getPlayer(), monster.isControllerHasAggro());
				}
			}
			byte direction = slea.readByte();
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showBuffeffect(c.getPlayer().getId(), skillid, 1, direction, false), false);
			c.getSession().write(MaplePacketCreator.enableActions());
			return;
		}

		if (slea.available() == 5) {
			pos = new Point(slea.readShort(), slea.readShort());
		} else {
			if (slea.readByte() == 0x80) {
				addedInfo = slea.readShort();
			}
		}

		if (skillLevel == 0 || skillLevel != __skillLevel) {
			AutobanManager.getInstance().addPoints(c.getPlayer().getClient(), 1000, 0, "Using a move skill he doesn't have (" + skill.getId() + ")");
		} else {
			if (c.getPlayer().isAlive()) {
				if (skill.getId() != 2311002 || c.getPlayer().canDoor()) {
					skill.getEffect(skillLevel).applyTo(c.getPlayer(), pos, addedInfo);
				} else {
					new ServernoticeMapleClientMessageCallback(5, c).dropMessage("Please wait 5 seconds before casting Mystic Door again");
					c.getSession().write(MaplePacketCreator.enableActions());
				}
			} else {
				c.getSession().write(MaplePacketCreator.enableActions());
			}
		}
	}
}
