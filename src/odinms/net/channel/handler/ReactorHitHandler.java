/*
 * This file is part of the OdinMS Maple Story Server
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
import odinms.server.maps.MapleReactor;
import odinms.tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lerk
 */
public class ReactorHitHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(ReactorHitHandler.class);

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// 8C 00 <int Reactor unique ID> <character relative position> 00 00 00 <character stance?>

		int oid = slea.readInt();
		int charPos = slea.readInt();
		short stance = slea.readShort();

		MapleReactor reactor = c.getPlayer().getMap().getReactorByOid(oid);
		if (reactor != null) {
			reactor.hitReactor(charPos, stance, c);
		} else { // player hit a destroyed reactor, likely due to lag
			log.trace(c.getPlayer().getName() + "<" + c.getPlayer().getId() +
				"> attempted to hit destroyed reactor with oid " + oid);
		}
	}
}
