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

import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.net.AbstractMaplePacketHandler;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class PetChatHandler extends AbstractMaplePacketHandler {

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		int petId = slea.readInt();
		slea.readInt();
		slea.readByte();
		int act = slea.readByte();
		String text = slea.readMapleAsciiString();
		MapleCharacter player = c.getPlayer();
		player.getMap().broadcastMessage(player, MaplePacketCreator.petChat(player.getId(), c.getPlayer().getPetIndex(petId), act, text), true);
	}
}
