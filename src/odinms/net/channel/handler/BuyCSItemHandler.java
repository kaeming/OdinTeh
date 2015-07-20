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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import odinms.client.MapleClient;
import odinms.client.MaplePet;
import odinms.net.AbstractMaplePacketHandler;
import odinms.net.MapleServerHandler;
import odinms.server.AutobanManager;
import odinms.server.MapleInventoryManipulator;
import odinms.tools.MaplePacketCreator;
import odinms.tools.data.input.SeekableLittleEndianAccessor;
import odinms.server.CashItemFactory;
import odinms.server.CashItemInfo;

/**
*
* @author Acrylic (Terry Han)
*/
public class BuyCSItemHandler extends AbstractMaplePacketHandler {
	private final static Logger log = LoggerFactory.getLogger(MapleServerHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		log.info(slea.toString());
		c.getSession().write(MaplePacketCreator.enableActions());
		/*int action = slea.readByte();
		if (action == 3) {
			int useNX = slea.readByte();
			int snCS = slea.readInt();
			CashItemInfo item = CashItemFactory.getItem(snCS);
				if (c.getPlayer().getCSPoints(useNX) >= item.getPrice()) {
					c.getPlayer().addCSPoints(useNX, -item.getPrice());
				} else {
					c.getSession().write(MaplePacketCreator.enableActions());
					AutobanManager.getInstance().autoban(c, "Trying to purchase from the CS when they have no NX");
					return;
				}
			if (item.getId() >= 5000000 && item.getId() <= 5000100) {
				int petId = MaplePet.createPet(item.getId());
				if (petId == -1) {
					return;
				}
				MapleInventoryManipulator.addById(c, item.getId(), (short) 1, "Cash Item was purchased.", null, petId);
			} else {
				MapleInventoryManipulator.addById(c, item.getId(), (short) item.getCount(), "Cash Item was purchased.");
			}
			c.getSession().write(MaplePacketCreator.showBoughtCSItem(item.getId()));
			c.getSession().write(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
			c.getSession().write(MaplePacketCreator.enableCSUse0());
			c.getSession().write(MaplePacketCreator.enableCSUse1());
			c.getSession().write(MaplePacketCreator.enableCSUse2());
		} /*else if (action == 5) {
			log.info(slea.toString());
				try{
					Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps = con.prepareStatement("DELETE FROM wishlist WHERE charid = ?");
                    ps.setInt(1, c.getPlayer().getId());
                    ps.executeUpdate();
                    ps.close();
                    
                    int i = 10;
                    while (i > 0)
                    {
                        int sn = slea.readInt();
                        ps = con.prepareStatement("INSERT INTO wishlist(charid, sn) VALUES(?, ?) ");
                        ps.setInt(1, c.getPlayer().getId());
                        ps.setInt(2, sn);
                        ps.executeUpdate();
                        ps.close();
                        i--;
                    }
				} catch (SQLException se) {}
				c.getSession().write(MaplePacketCreator.updateWishList(c.getPlayer().getId()));
		}*/
	}
}
