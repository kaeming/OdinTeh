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

import odinms.client.MapleClient;
import odinms.net.AbstractMaplePacketHandler;
import odinms.scripting.npc.NPCConversationManager;
import odinms.scripting.npc.NPCScriptManager;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
 *
 * @author Matze
 */
public class NPCMoreTalkHandler extends AbstractMaplePacketHandler {

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		byte lastMsg = slea.readByte();
		byte action = slea.readByte();

		if (lastMsg == 2) {
			String returnText = slea.readMapleAsciiString();
			NPCConversationManager cm = NPCScriptManager.getInstance().getCM(c);
			cm.setGetText(returnText);
			NPCScriptManager.getInstance().action(c, action, lastMsg, (byte) -1);
		} else {
			byte selection = -1;
			if (slea.available() > 0) {
				selection = slea.readByte();
			}
			NPCScriptManager.getInstance().action(c, action, lastMsg, selection);
		}
	}
}
