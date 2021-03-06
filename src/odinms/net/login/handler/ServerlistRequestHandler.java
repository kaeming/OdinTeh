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

package odinms.net.login.handler;

import odinms.client.MapleClient;
import odinms.net.AbstractMaplePacketHandler;
import odinms.net.login.LoginServer;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class ServerlistRequestHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		c.getSession().write(MaplePacketCreator.getServerList(0, "Zenith", LoginServer.getInstance().getLoad()));
		//c.getSession().write(MaplePacketCreator.getServerList(1, "Zenith", LoginServer.getInstance().getChannels(), 1200));
		//c.getSession().write(MaplePacketCreator.getServerList(2, "Zenith", LoginServer.getInstance().getChannels(), 1200));
		//c.getSession().write(MaplePacketCreator.getServerList(3, "Zenith", LoginServer.getInstance().getChannels(), 1200));
		c.getSession().write(MaplePacketCreator.getEndOfServerList());
	}
}
