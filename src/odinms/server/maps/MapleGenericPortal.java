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

package odinms.server.maps;

import java.awt.Point;

import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.anticheat.CheatingOffense;
import odinms.net.channel.ChannelServer;
import odinms.scripting.portal.PortalScriptManager;
import odinms.server.MaplePortal;
import odinms.tools.MaplePacketCreator;

public class MapleGenericPortal implements MaplePortal {
	private String name;
	private String target;
	private Point position;
	private int targetmap;
	private int type;
	private int id;
	private String scriptName;

	public MapleGenericPortal(int type) {
		this.type = type;
	}
	
	@Override
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id  = id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Point getPosition() {
		return position;
	}

	@Override
	public String getTarget() {
		return target;
	}

	@Override
	public int getTargetMapId() {
		return targetmap;
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public String getScriptName() {
		return scriptName;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public void setPosition(Point position) {
		this.position = position;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public void setTargetMapId(int targetmapid) {
		this.targetmap = targetmapid;
	}

	@Override
	public void setScriptName(String scriptName) {
		this.scriptName = scriptName;
	}
	
	@Override
	public void enterPortal(MapleClient c) {
		MapleCharacter player = c.getPlayer();
		double distanceSq = getPosition().distanceSq(player.getPosition());
		if (distanceSq > 22500) {
			player.getCheatTracker().registerOffense(CheatingOffense.USING_FARAWAY_PORTAL, "D" + Math.sqrt(distanceSq));
		}
		
		boolean changed = false;
		if (getScriptName() != null) {
			changed = PortalScriptManager.getInstance().executePortalScript(this, c);
		} else if (getTargetMapId() != 999999999) {
			MapleMap to;
			if (player.getEventInstance() == null) {
				to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(getTargetMapId());
			} else {
				to = player.getEventInstance().getMapInstance(getTargetMapId());
			}
			MaplePortal pto = to.getPortal(getTarget());
			if (pto == null) {
				pto = to.getPortal(0);
			}
			c.getPlayer().changeMap(to, pto);
			changed = true;
		}
		if (!changed) {
			c.getSession().write(MaplePacketCreator.enableActions());
		}
	}
}
