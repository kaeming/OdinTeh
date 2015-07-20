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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static odinms.client.messages.CommandProcessor.getOptionalIntArg;
import odinms.client.IItem;
import odinms.client.Item;
import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.MapleInventoryType;
import odinms.client.MapleJob;
import odinms.client.MaplePet;
import odinms.client.MapleStat;
import odinms.client.SkillFactory;
import odinms.client.messages.Command;
import odinms.client.messages.CommandDefinition;
import odinms.client.messages.IllegalCommandSyntaxException;
import odinms.client.messages.MessageCallback;
import odinms.client.messages.ServernoticeMapleClientMessageCallback;
import odinms.server.MapleInventoryManipulator;
import odinms.server.MapleItemInformationProvider;
import odinms.server.MapleShop;
import odinms.server.MapleShopFactory;
import odinms.tools.MaplePacketCreator;
import odinms.tools.Pair;

public class CharCommands implements Command {

	private MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

	@SuppressWarnings("static-access")
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception, IllegalCommandSyntaxException {
		MapleCharacter player = c.getPlayer();
		if (splitted[0].equals("!lowhp")) {
			player.setHp(1);
			player.setMp(500);
			player.updateSingleStat(MapleStat.HP, 1);
			player.updateSingleStat(MapleStat.MP, 500);
		} else if (splitted[0].equals("!fullhp")) {
			player.addMPHP(player.getMaxHp() - player.getHp(), player.getMaxMp() - player.getMp());
		} else if (splitted[0].equals("!skill")) {
			int skill = Integer.parseInt(splitted[1]);
			int level = getOptionalIntArg(splitted, 2, 1);
			int masterlevel = getOptionalIntArg(splitted, 3, 1);
			c.getPlayer().changeSkillLevel(SkillFactory.getSkill(skill), level, masterlevel);
		} else if (splitted[0].equals("!ap")) {
			player.setRemainingAp(getOptionalIntArg(splitted, 1, 1));
			player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
		} else if (splitted[0].equals("!sp")) {
			player.setRemainingSp(getOptionalIntArg(splitted, 1, 1));
			player.updateSingleStat(MapleStat.AVAILABLESP, player.getRemainingSp());
		} else if (splitted[0].equals("!job")) {
			c.getPlayer().changeJob(MapleJob.getById(Integer.parseInt(splitted[1])));
		} else if (splitted[0].equals("!whereami")) {
			new ServernoticeMapleClientMessageCallback(c).dropMessage("You are on map " +
					c.getPlayer().getMap().getId());
		} else if (splitted[0].equals("!shop")) {
			MapleShopFactory sfact = MapleShopFactory.getInstance();
			MapleShop shop = sfact.getShop(getOptionalIntArg(splitted, 1, 1));
			shop.sendShop(c);
		} else if (splitted[0].equals("!levelup")) {
			c.getPlayer().levelUp();
			int newexp = c.getPlayer().getExp();
			if (newexp < 0) {
				c.getPlayer().gainExp(-newexp, false, false);
			}
		} else if (splitted[0].equals("!item")) {
			short quantity = (short) getOptionalIntArg(splitted, 2, 1);
			int itemId = Integer.parseInt(splitted[1]);
			if (ii.getSlotMax(itemId) > 0) {
				if (itemId >= 5000000 && itemId <= 5000100) {
					if (quantity > 1) {
						quantity = 1;
					}
					int petId = MaplePet.createPet(itemId);
					MapleInventoryManipulator.addById(c, itemId, quantity, c.getPlayer().getName() + "used !item with quantity " + quantity, player.getName(), petId);
					return;
				}
				MapleInventoryManipulator.addById(c, itemId, quantity, c.getPlayer().getName() + "used !item with quantity " + quantity, player.getName());
			} else {
				mc.dropMessage("Item " + itemId + " not found.");
			}
		} else if (splitted[0].equals("!drop")) {
			int itemId = Integer.parseInt(splitted[1]);
			short quantity = (short) getOptionalIntArg(splitted, 2, 1);
			IItem toDrop;
			if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
				toDrop = ii.getEquipById(itemId);
			} else {
				toDrop = new Item(itemId, (byte) 0, (short) quantity);
			}
			StringBuilder logMsg = new StringBuilder("Created by ");
			logMsg.append(c.getPlayer().getName());
			logMsg.append(" using !drop. Quantity: ");
			logMsg.append(quantity);
			toDrop.log(logMsg.toString(), false);
			toDrop.setOwner(player.getName());
			c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);
		} else if (splitted[0].equals("!level")) {
			int quantity = Integer.parseInt(splitted[1]);
			c.getPlayer().setLevel(quantity);
			c.getPlayer().levelUp();
			int newexp = c.getPlayer().getExp();
			if (newexp < 0) {
				c.getPlayer().gainExp(-newexp, false, false);
			}
		} else if (splitted[0].equals("!online")) {
			mc.dropMessage("Characters connected to channel " + c.getChannel() + ":");
			Collection<MapleCharacter> chrs = c.getChannelServer().getInstance(c.getChannel()).getPlayerStorage().getAllCharacters();
			for (MapleCharacter chr : chrs) {
				mc.dropMessage(chr.getName() + " at map ID: " + chr.getMapId());
			}
			mc.dropMessage("Total characters on channel " + c.getChannel() + ": " + chrs.size());
		} else if (splitted[0].equals("!statreset")) {
			int str = c.getPlayer().getStr();
			int dex = c.getPlayer().getDex();
			int int_ = c.getPlayer().getInt();
			int luk = c.getPlayer().getLuk();
			int newap = c.getPlayer().getRemainingAp() + (str - 4) + (dex - 4) + (int_ - 4) + (luk - 4);
			c.getPlayer().setStr(4);
			c.getPlayer().setDex(4);
			c.getPlayer().setInt(4);
			c.getPlayer().setLuk(4);
			c.getPlayer().setRemainingAp(newap);
			List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
			stats.add(new Pair<MapleStat, Integer>(MapleStat.STR, Integer.valueOf(4)));
			stats.add(new Pair<MapleStat, Integer>(MapleStat.DEX, Integer.valueOf(4)));
			stats.add(new Pair<MapleStat, Integer>(MapleStat.INT, Integer.valueOf(4)));
			stats.add(new Pair<MapleStat, Integer>(MapleStat.LUK, Integer.valueOf(4)));
			stats.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLEAP, Integer.valueOf(newap)));
			c.getSession().write(MaplePacketCreator.updatePlayerStats(stats));
		} else if (splitted[0].equals("!gmpacket")) {
			int type = Integer.parseInt(splitted[1]);
			int mode = Integer.parseInt(splitted[2]);
			c.getSession().write(MaplePacketCreator.sendGMOperation(type, mode));
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[]{
					new CommandDefinition("lowhp", "", "", 100),
					new CommandDefinition("fullhp", "", "", 100),
					new CommandDefinition("skill", "", "", 100),
					new CommandDefinition("ap", "", "", 100),
					new CommandDefinition("sp", "", "", 100),
					new CommandDefinition("job", "", "", 100),
					new CommandDefinition("whereami", "", "", 100),
					new CommandDefinition("shop", "", "", 100),
					new CommandDefinition("levelup", "", "", 100),
					new CommandDefinition("item", "", "", 100),
					new CommandDefinition("drop", "", "", 100),
					new CommandDefinition("level", "", "", 100),
					new CommandDefinition("online", "", "", 100),
					new CommandDefinition("ring", "", "", 100),
					new CommandDefinition("statreset", "", "", 100),
					new CommandDefinition("gmpacket", "", "", 100)
				};
	}
}
