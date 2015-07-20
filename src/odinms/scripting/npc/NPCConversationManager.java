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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package odinms.scripting.npc;

import java.util.LinkedList;
import java.util.List;

import odinms.client.IItem;
import odinms.client.Item;
import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.MapleInventory;
import odinms.client.MapleInventoryType;
import odinms.client.MapleJob;
import odinms.client.SkillFactory;
import odinms.scripting.AbstractPlayerInteraction;
import odinms.scripting.event.EventManager;
import odinms.server.MapleInventoryManipulator;
import odinms.server.MapleItemInformationProvider;
import odinms.server.MapleShopFactory;
import odinms.server.quest.MapleQuest;
import odinms.tools.MaplePacketCreator;
import odinms.client.MapleStat;
import odinms.net.world.guild.MapleGuild;
import odinms.server.MapleSquad;
import odinms.server.MapleSquadType;
import odinms.server.MapleStatEffect;
import odinms.server.maps.MapleMap;

/**
 *
 * @author Matze
 */
public class NPCConversationManager extends AbstractPlayerInteraction {
	private MapleClient c;
	private int npc;
	private String getText;

	public NPCConversationManager(MapleClient c, int npc) {
		super(c);
		this.c = c;
		this.npc = npc;
	}

	public void dispose() {
		NPCScriptManager.getInstance().dispose(this);
	}

	public void sendNext(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 01"));
	}

	public void sendPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 00"));
	}

	public void sendNextPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 01"));
	}

	public void sendOk(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00"));
	}

	public void sendYesNo(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 1, text, ""));
	}

	public void sendAcceptDecline(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0x0C, text, ""));
	}

	public void sendSimple(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 4, text, ""));
	}

	public void sendStyle(String text, int styles[]) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkStyle(npc, text, styles));
	}

	public void sendGetNumber(String text, int def, int min, int max) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkNum(npc, text, def, min, max));
	}

	public void sendGetText(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkText(npc, text));
	}

	public void setGetText(String text) {
		this.getText = text;
	}

	public String getText() {
		return this.getText;
	}

	public void openShop(int id) {
		MapleShopFactory.getInstance().getShop(id).sendShop(getClient());
	}

	public void changeJob(MapleJob job) {
		getPlayer().changeJob(job);
	}

	public MapleJob getJob() {
		return getPlayer().getJob();
	}

	public void startQuest(int id) {
		MapleQuest.getInstance(id).start(getPlayer(), npc);
	}

	public void completeQuest(int id) {
		MapleQuest.getInstance(id).complete(getPlayer(), npc);
	}

	public void forfeitQuest(int id) {
		MapleQuest.getInstance(id).forfeit(getPlayer());
	}

	/**
	 * use getPlayer().getMeso() instead
	 * @return
	 */
	@Deprecated
	public int getMeso() {
		return getPlayer().getMeso();
	}

	public void gainMeso(int gain) {
		getPlayer().gainMeso(gain, true, false, true);
	}

	public void gainExp(int gain) {
		getPlayer().gainExp(gain, true, true);
	}

	public int getNpc() {
		return npc;
	}

	/**
	 * use getPlayer().getLevel() instead
	 * @return
	 */
	@Deprecated
	public int getLevel() {
		return getPlayer().getLevel();
	}

	public void unequipEverything() {
		MapleInventory equipped = getPlayer().getInventory(MapleInventoryType.EQUIPPED);
		MapleInventory equip = getPlayer().getInventory(MapleInventoryType.EQUIP);
		List<Byte> ids = new LinkedList<Byte>();
		for (IItem item : equipped.list()) {
			ids.add(item.getPosition());
		}
		for (byte id : ids) {
			MapleInventoryManipulator.unequip(getC(), id, equip.getNextFreeSlot());
		}
	}

	public void teachSkill(int id, int level, int masterlevel) {
		getPlayer().changeSkillLevel(SkillFactory.getSkill(id), level, masterlevel);
	}

	/**
	 * Use getPlayer() instead (for consistency with MapleClient)
	 * @return
	 */
	@Deprecated
	public MapleCharacter getChar() {
		return getPlayer();
	}

	public MapleClient getC() {
		return getClient();
	}

	public void rechargeStars() {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		IItem stars = getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) 1);
		if (ii.isThrowingStar(stars.getItemId()) || ii.isBullet(stars.getItemId())) {
			stars.setQuantity(ii.getSlotMax(stars.getItemId()));
			getC().getSession().write(MaplePacketCreator.updateInventorySlot(MapleInventoryType.USE, (Item) stars));
		}
	}

	public EventManager getEventManager(String event) {
		return getClient().getChannelServer().getEventSM().getEventManager(event);
	}

	public void showEffect(String effect) {
		getPlayer().getMap().broadcastMessage(MaplePacketCreator.showEffect(effect));
	}

	public void playSound(String sound) {
		getClient().getPlayer().getMap().broadcastMessage(MaplePacketCreator.playSound(sound));
	}

	@Override
	public String toString() {
		return "Conversation with NPC: " + npc;
	}

	public void updateBuddyCapacity(int capacity) {
		getPlayer().setBuddyCapacity(capacity);
	}

	public int getBuddyCapacity() {
		return getPlayer().getBuddyCapacity();
	}

	public void setHair(int hair) {
		c.getPlayer().setHair(hair);
		c.getPlayer().updateSingleStat(MapleStat.HAIR, hair);
		c.getPlayer().equipChanged();
	}

	public void setFace(int face) {
		c.getPlayer().setFace(face);
		c.getPlayer().updateSingleStat(MapleStat.FACE, face);
		c.getPlayer().equipChanged();
	}

	@SuppressWarnings("static-access")
	public void setSkin(int color) {
		c.getPlayer().setSkinColor(c.getPlayer().getSkinColor().getById(color));
		c.getPlayer().updateSingleStat(MapleStat.SKIN, color);
		c.getPlayer().equipChanged();
	}

	public MapleSquad createMapleSquad(MapleSquadType type) {
		MapleSquad squad = new MapleSquad(c.getChannel(), getPlayer());
		if (getSquadState(type) == 0) {
			c.getChannelServer().addMapleSquad(squad, type);
		} else {
			return null;
		}
		return squad;
	}

	public MapleCharacter getSquadMember(MapleSquadType type, int index) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		MapleCharacter ret = null;
		if (squad != null) {
			ret = squad.getMembers().get(index);
		}
		return ret;
	}

	public int getSquadState(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			return squad.getStatus();
		} else {
			return 0;
		}
	}

	public void setSquadState(MapleSquadType type, int state) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.setStatus(state);
		}
	}

	public boolean checkSquadLeader(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.getLeader().getId() == getPlayer().getId()) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public void removeMapleSquad(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.getLeader().getId() == getPlayer().getId()) {
				squad.clear();
				c.getChannelServer().removeMapleSquad(squad, type);
			}
		}
	}

	public int numSquadMembers(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		int ret = 0;
		if (squad != null) {
			ret = squad.getSquadSize();
		}
		return ret;
	}

	public boolean isSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		boolean ret = false;
		if (squad.containsMember(getPlayer())) {
			ret = true;
		}
		return ret;
	}

	public void addSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.addMember(getPlayer());
		}
	}

	public void removeSquadMember(MapleSquadType type, MapleCharacter chr, boolean ban) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.banMember(chr, ban);
		}
	}

	public void removeSquadMember(MapleSquadType type, int index, boolean ban) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			MapleCharacter chr = squad.getMembers().get(index);
			squad.banMember(chr, ban);
		}
	}

	public boolean canAddSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.isBanned(getPlayer())) {
				return false;
			} else {
				return true;
			}
		}
		return false;
	}

	public void warpSquadMembers(MapleSquadType type, int mapId) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		MapleMap map = c.getChannelServer().getMapFactory().getMap(mapId);
		if (squad != null) {
			if (checkSquadLeader(type)) {
				for (MapleCharacter chr : squad.getMembers()) {
					chr.changeMap(map, map.getPortal(0));
				}
			}
		}
	}

	public void resetReactors() {
		c.getPlayer().getMap().resetReactors();
	}

	public void displayGuildRanks() {
		MapleGuild.displayGuildRanks(getClient(), npc);
	}

	public void sendCygnusCreation() {
		c.getSession().write(MaplePacketCreator.sendCygnusCreation());
	}
	
	public MapleStatEffect getItemEffect(int itemId) {
		return MapleItemInformationProvider.getInstance().getItemEffect(itemId);
	}
}
