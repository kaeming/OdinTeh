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

import java.util.Random;

import odinms.client.ExpTable;
import odinms.client.MapleClient;
import odinms.client.MapleInventoryType;
import odinms.client.MaplePet;
import odinms.net.AbstractMaplePacketHandler;
import odinms.server.MapleInventoryManipulator;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class PetFoodHandler extends AbstractMaplePacketHandler {

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		if (c.getPlayer().getNoPets() == 0) {
			return;
		}
		
		int previousFullness = 100;
		int slot = 0;
		
		MaplePet[] pets = c.getPlayer().getPets();
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				if (pets[i].getFullness() < previousFullness) {
					slot = i;
					previousFullness = pets[i].getFullness();
				}
			}
		}
		
		MaplePet pet = c.getPlayer().getPet(slot);
		
		slea.readInt();
		slea.readShort();
		int itemId = slea.readInt();

		boolean gainCloseness = false;
		
		Random rand = new Random();
		int random = rand.nextInt(101);
		if (random <= 50) {
			gainCloseness = true;
		}
		if (pet.getFullness() < 100) {
			int newFullness = pet.getFullness() + 30;
			if (newFullness > 100) {
				newFullness = 100;
			}
			pet.setFullness(newFullness);
			if (gainCloseness && pet.getCloseness() < 30000) {
				int newCloseness = pet.getCloseness() + (1 * c.getChannelServer().getPetExpRate());
				if (newCloseness > 30000) {
				    newCloseness = 30000;
				}
				pet.setCloseness(newCloseness);
				if (newCloseness >= ExpTable.getClosenessNeededForLevel(pet.getLevel() + 1)) {
					pet.setLevel(pet.getLevel() + 1);
					c.getSession().write(MaplePacketCreator.showOwnPetLevelUp(c.getPlayer().getPetIndex(pet)));
					c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.showPetLevelUp(c.getPlayer(), c.getPlayer().getPetIndex(pet)));
				}
			}
			c.getSession().write(MaplePacketCreator.updatePet(pet, true));
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), slot, 1, true), true);
		} else {
			if (gainCloseness) {
				int newCloseness = pet.getCloseness() - (1 * c.getChannelServer().getPetExpRate());
				if (newCloseness < 0) {
				    newCloseness = 0;
				}
				pet.setCloseness(newCloseness);
				if (newCloseness < ExpTable.getClosenessNeededForLevel(pet.getLevel())) {
					pet.setLevel(pet.getLevel() - 1);
				}
			}
			c.getSession().write(MaplePacketCreator.updatePet(pet, true));
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), slot, 1, false), true);
		}
		MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, itemId, 1, true, false);
	}
}
