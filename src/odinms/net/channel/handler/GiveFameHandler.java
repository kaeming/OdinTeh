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
import odinms.client.MapleStat;
import odinms.client.anticheat.CheatingOffense;
import odinms.net.AbstractMaplePacketHandler;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;

public class GiveFameHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		int who = slea.readInt();
		int mode = slea.readByte();
                
		int famechange = mode == 0 ? -1 : 1;
		MapleCharacter target = (MapleCharacter) c.getPlayer().getMap().getMapObject(who);
                
		if (target == c.getPlayer()) { // faming self
			c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.FAMING_SELF);
			return;
		} else if (c.getPlayer().getLevel() < 15) {
			c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.FAMING_UNDER_15);
			return;
		}
		switch (c.getPlayer().canGiveFame(target)) {
			case OK:
				target.addFame(famechange);
				c.getPlayer().hasGivenFame(target);
				c.getSession().write(MaplePacketCreator.giveFameResponse(mode, target.getName(), target.getFame()));
				target.updateSingleStat(MapleStat.FAME, target.getFame());
				target.getClient().getSession().write(MaplePacketCreator.receiveFame(mode, c.getPlayer().getName()));
				break;
			case NOT_TODAY:
				c.getSession().write(MaplePacketCreator.giveFameErrorResponse(3));
				break;
			case NOT_THIS_MONTH:
				c.getSession().write(MaplePacketCreator.giveFameErrorResponse(4));
				break;
		}
	}
}
