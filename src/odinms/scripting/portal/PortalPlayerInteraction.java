package odinms.scripting.portal;

import odinms.client.MapleClient;
import odinms.scripting.AbstractPlayerInteraction;
import odinms.server.MaplePortal;

public class PortalPlayerInteraction extends AbstractPlayerInteraction {
	private MaplePortal portal;
	
	public PortalPlayerInteraction(MapleClient c, MaplePortal portal) {
		super (c);
		this.portal = portal;
	}
	
	public MaplePortal getPortal() {
		return portal;
	}
}
