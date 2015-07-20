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
import odinms.client.messages.CommandProcessor;
import odinms.net.AbstractMaplePacketHandler;
import odinms.tools.MaplePacketCreator;
import odinms.tools.StringUtil;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class GeneralChatHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		String text = slea.readMapleAsciiString();
		int show = slea.readByte();

		if (!CommandProcessor.getInstance().processCommand(c, text)) {
			if (StringUtil.countCharacters(text, '@') > 4 || StringUtil.countCharacters(text, '%') > 4 ||
				StringUtil.countCharacters(text, '+') > 6 || StringUtil.countCharacters(text, '$') > 6 ||
				StringUtil.countCharacters(text, '&') > 6 || StringUtil.countCharacters(text, '~') > 6) {
				text = "I suck, big time. Don't listen to anything I say.";
			}
			c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getChatText(c.getPlayer().getId(), text, c.getPlayer().isGM() && c.getChannelServer().allowGmWhiteText(), show));
		}
	}
}