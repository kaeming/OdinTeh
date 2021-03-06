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

package odinms.client.messages.commands;

import java.awt.Point;

import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.anticheat.CheatingOffense;
import odinms.client.messages.Command;
import odinms.client.messages.CommandDefinition;
import odinms.client.messages.IllegalCommandSyntaxException;
import odinms.client.messages.MessageCallback;
import odinms.server.MaplePortal;
import odinms.server.TimerManager;
import odinms.server.life.MobSkill;
import odinms.server.life.MobSkillFactory;
import odinms.server.maps.MapleDoor;
import odinms.server.quest.MapleQuest;
import odinms.tools.HexTool;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.output.MaplePacketLittleEndianWriter;

public class DebugCommands implements Command {
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception,
																					IllegalCommandSyntaxException {
		MapleCharacter player = c.getPlayer();
		if (splitted[0].equals("!resetquest")) {
			MapleQuest.getInstance(Integer.parseInt(splitted[1])).forfeit(c.getPlayer());
		} else if (splitted[0].equals("!nearestPortal")) {
			final MaplePortal portal = player.getMap().findClosestSpawnpoint(player.getPosition());
			mc.dropMessage(portal.getName() + " id: " + portal.getId() + " script: " + portal.getScriptName());
		} else if (splitted[0].equals("!spawndebug")) {
			c.getPlayer().getMap().spawnDebug(mc);
		} else if (splitted[0].equals("!door")) {
			Point doorPos = new Point(player.getPosition());
			doorPos.y -= 270;
			MapleDoor door = new MapleDoor(c.getPlayer(), doorPos);
			door.getTarget().addMapObject(door);
			// c.getSession().write(MaplePacketCreator.spawnDoor(/*c.getPlayer().getId()*/ 0x1E47, door.getPosition(),
			// false));
			/* c.getSession().write(MaplePacketCreator.saveSpawnPosition(door.getPosition())); */
			MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
			mplew.write(new byte[] { (byte) 0xB9, 0, 0, 0x47, 0x1E, 0, 0, 0x0A, 0x04, 0x76, (byte) 0xFF });
			c.getSession().write(mplew.getPacket());
			mplew = new MaplePacketLittleEndianWriter();
			mplew.write(new byte[] { 0x36, 0, 0, (byte) 0xEF, 0x1C, 0x0D, 0x4C, 0x3E, 0x1D, 0x0D, 0x0A, 0x04, 0x76, (byte) 0xFF });
			c.getSession().write(mplew.getPacket());
			c.getSession().write(MaplePacketCreator.enableActions());
			door = new MapleDoor(door);
			door.getTown().addMapObject(door);
		} else if (splitted[0].equals("!timerdebug")) {
			TimerManager.getInstance().dropDebugInfo(mc);
		} else if (splitted[0].equals("!threads")) {
			Thread[] threads = new Thread[Thread.activeCount()];
			Thread.enumerate(threads);
			String filter = "";
			if (splitted.length > 1) {
				filter = splitted[1];
			}
			for (int i = 0; i < threads.length; i++) {
				String tstring = threads[i].toString();
				if (tstring.toLowerCase().indexOf(filter.toLowerCase()) > -1) {
					mc.dropMessage(i + ": " + tstring);
				}
			}
		} else if (splitted[0].equals("!showtrace")) {
			if (splitted.length < 2) {
				throw new IllegalCommandSyntaxException(2);
			}
			Thread[] threads = new Thread[Thread.activeCount()];
			Thread.enumerate(threads);
			Thread t = threads[Integer.parseInt(splitted[1])];
			mc.dropMessage(t.toString() + ":");
			for (StackTraceElement elem : t.getStackTrace()) {
				mc.dropMessage(elem.toString());
			}
		} else if (splitted[0].equals("!fakerelog")) {
			c.getSession().write(MaplePacketCreator.getCharInfo(player));
			player.getMap().removePlayer(player);
			player.getMap().addPlayer(player);
			/*int i = 1;
			if (c.getPlayer().getNoPets() > 0) {
				for (MaplePet pet : c.getPlayer().getPets()) {
					List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
					stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(pet.getUniqueId())));
					// Write the stat update to the player...
					c.getSession().write(MaplePacketCreator.updatePlayerStats(stats, false, true, i));
					c.getSession().write(MaplePacketCreator.enableActions());
					i++;
				}
			}*/
		} else if (splitted[0].equals("!toggleoffense")) {
			try {
				CheatingOffense co = CheatingOffense.valueOf(splitted[1]);
				co.setEnabled(!co.isEnabled());
			} catch (IllegalArgumentException iae) {
				mc.dropMessage("Offense " + splitted[1] + " not found");
			}
		} else if (splitted[0].equals("!tdrops")) {
			player.getMap().toggleDrops();
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("resetquest", "", "", 1000),
			new CommandDefinition("nearestPortal", "", "", 1000),
			new CommandDefinition("spawndebug", "", "", 1000),
			new CommandDefinition("timerdebug", "", "", 1000),
			new CommandDefinition("threads", "", "", 1000),
			new CommandDefinition("showtrace", "", "", 1000),
			new CommandDefinition("toggleoffense", "", "", 1000),
			new CommandDefinition("fakerelog", "", "", 1000),
			new CommandDefinition("tdrops", "", "", 100),
		};
	}

}
