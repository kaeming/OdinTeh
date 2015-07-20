/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 208, Patrick Huy <patrick.huy@frz.cc> 
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
package odinms.tools;

import odinms.net.world.guild.MapleGuildCharacter;
import odinms.net.world.guild.MapleGuild;
import odinms.net.world.guild.MapleGuildSummary;
import java.awt.Point;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import odinms.client.BuddylistEntry;
import odinms.client.IEquip;
import odinms.client.IItem;
import odinms.client.ISkill;
import odinms.client.Item;
import odinms.client.MapleBuffStat;
import odinms.client.MapleCharacter;
import odinms.client.MapleClient;
import odinms.client.MapleInventory;
import odinms.client.MapleInventoryType;
import odinms.client.MapleKeyBinding;
import odinms.client.MaplePet;
import odinms.client.MapleQuestStatus;
import odinms.client.MapleStat;
import odinms.client.IEquip.ScrollResult;
import odinms.client.MapleDisease;
import odinms.client.MapleJob;
import odinms.client.SkillFactory;
import odinms.client.SkillMacro;
import odinms.client.status.MonsterStatus;
import odinms.database.DatabaseConnection;
import odinms.net.ByteArrayMaplePacket;
import odinms.net.MaplePacket;
import odinms.net.SendPacketOpcode;
import odinms.net.channel.handler.AbstractDealDamageHandler.AttackInfo;
import odinms.net.channel.handler.SummonDamageHandler.SummonAttackEntry;
import odinms.net.world.MapleParty;
import odinms.net.world.MaplePartyCharacter;
import odinms.net.world.PartyOperation;
import odinms.server.constants.Items;
import odinms.server.MapleItemInformationProvider;
import odinms.server.MaplePlayerShop;
import odinms.server.MaplePlayerShopItem;
import odinms.server.MapleShopItem;
import odinms.server.MapleTrade;
import odinms.server.life.MapleMonster;
import odinms.server.life.MapleNPC;
import odinms.server.maps.MapleMap;
import odinms.server.maps.MapleReactor;
import odinms.server.movement.LifeMovementFragment;
import odinms.tools.data.output.LittleEndianWriter;
import odinms.tools.data.output.MaplePacketLittleEndianWriter;
import odinms.server.life.MobSkill;
import odinms.server.maps.MapleMist;
import odinms.server.maps.MapleSummon;

/**
 * Provides all MapleStory packets needed in one place.
 * 
 * @author Frz
 * @since Revision 259
 * @version 1.0
 */
public class MaplePacketCreator {
	private static Logger log = LoggerFactory.getLogger(MaplePacketCreator.class);
	private final static byte[] CHAR_INFO_MAGIC = new byte[]{(byte) 0xff, (byte) 0xc9, (byte) 0x9a, 0x3b};
	private final static byte[] ITEM_MAGIC = new byte[]{(byte) (byte) 0x80, 5};
	public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();
	private final static long FT_UT_OFFSET = 116444592000000000L;
	//private final static long NO_EXPIRATION = 150842304000000000L;

	private static long getKoreanTimestamp(long realTimestamp) {
		long time = (realTimestamp / 1000 / 60); //convert to minutes

		return ((time * 600000000) + FT_UT_OFFSET);
	}

	private static long getTime(long realTimestamp) {
		long time = (realTimestamp / 1000); // convert to seconds

		return ((time * 10000000) + FT_UT_OFFSET);
	}

	/**
	 * Sends a hello packet.
	 * 
	 * @param mapleVersion The maple client version.
	 * @param sendIv the IV used by the server for sending
	 * @param recvIv the IV used by the server for receiving
	 */
	public static MaplePacket getHello(short mapleVersion, byte[] sendIv, byte[] recvIv, boolean testServer) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(0x0d);
		mplew.writeShort(mapleVersion);
		mplew.write(new byte[]{0, 0});
		mplew.write(recvIv);
		mplew.write(sendIv);
		mplew.write(testServer ? 5 : 8);
		return mplew.getPacket();
	}

	/**
	 * Sends a ping packet.
	 * 
	 * @return The packet.
	 */
	public static MaplePacket getPing() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.PING.getValue());
		return mplew.getPacket();
	}

	/**
	 * Gets a login failed packet.
	 * 
	 * Possible values for <code>reason</code>:<br>
	 * 3: ID deleted or blocked<br>
	 * 4: Incorrect password<br>
	 * 5: Not a registered id<br>
	 * 6: System error<br>
	 * 7: Already logged in<br>
	 * 8: System error<br>
	 * 9: System error<br>
	 * 10: Cannot process so many connections<br>
	 * 11: Only users older than 0x20, can use this channel
	 * 
	 * @param reason The reason logging in failed.
	 * @return The login failed packet.
	 */
	public static MaplePacket getLoginFailed(int reason) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeInt(reason);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket getPermBan(byte reason) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		// Response.WriteHexString("0, 0, 2, 0, 1, 1, 1, 1, 1, 00");
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeShort(2); // Account is banned

		mplew.write(0x0);
		mplew.write(reason);
		mplew.write(new byte[]{1, 1, 1, 1, 0});
		return mplew.getPacket();
	}

	public static MaplePacket getTempBan(long timestampTill, byte reason) {

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(17);
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.write(2);
		mplew.write(new byte[]{0, 0, 0, 0, 0}); // Account is banned

		mplew.write(reason);
		mplew.writeLong(timestampTill); // Tempban date is handled as a 64-bit long, number of 100NS intervals since
		// 1/1/1601. Lulz.

		return mplew.getPacket();
	}

	/**
	 * Gets a successful authentication and PIN Request packet.
	 * 
	 * @param account The account name.
	 * @return The PIN request packet.
	 */
	public static MaplePacket getAuthSuccessRequestPin(String account, boolean gm) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.writeInt(0); //user id
		mplew.write(0); //gender (0x0a == gender select, 0x0b == pin select)
		mplew.write(gm ? 1 : 0);
		mplew.write(0); //admin, doesn't let them chat
		mplew.write(0);
		mplew.writeMapleAsciiString(account);
		mplew.write(0);
		mplew.write(0);
		mplew.writeLong(0);
		mplew.writeLong(0); //creation time

		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet detailing a PIN operation.
	 * 
	 * Possible values for <code>mode</code>:<br>
	 * 0 - PIN was accepted<br>
	 * 1 - Register a new PIN<br>
	 * 2 - Invalid pin / Reenter<br>
	 * 3 - Connection failed due to system error<br>
	 * 4 - Enter the pin
	 * 
	 * @param mode The mode.
	 */
	public static MaplePacket pinOperation(byte mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.PIN_OPERATION.getValue());
		mplew.write(mode);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet requesting the client enter a PIN.
	 * 
	 * @return The request PIN packet.
	 */
	public static MaplePacket requestPin() {
		return pinOperation((byte) 4);
	}

	/**
	 * Gets a packet requesting the PIN after a failed attempt.
	 * 
	 * @return The failed PIN packet.
	 */
	public static MaplePacket requestPinAfterFailure() {
		return pinOperation((byte) 2);
	}

	/**
	 * Gets a packet saying the PIN has been accepted.
	 * 
	 * @return The PIN accepted packet.
	 */
	public static MaplePacket pinAccepted() {
		return pinOperation((byte) 0);
	}

	/**
	 * Gets a packet detailing a server and its channels.
	 * 
	 * @param serverIndex The index of the server to create information about.
	 * @param serverName The name of the server.
	 * @param channelLoad Load of the channel - 120, seems to be max.
	 * @return The server info packet.
	 */
	public static MaplePacket getServerList(int serverIndex, String serverName, Map<Integer, Integer> channelLoad) {
		/*
		 * 0x0B, 0, 0, 6, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0, 0, 0, 0x64, 0, 0x64, 0, 0, 0x13, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x5E, 4, 0, 0, 0, 00
		 * 0, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x32, 0x25, 1, 0, 0, 0, 1, 0, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x33, (byte) 0xF6, 0, 0, 0, 0, 2, 0, 08
		 * 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x34, (byte) 0xBC, 0, 0, 0, 0, 3, 0, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x35, (byte) 0xE7, 0, 0, 0, 0, 4, 0, 8, 0, 53
		 * 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x36, (byte) 0xBC, 0, 0, 0, 0, 5, 0, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x37, (byte) 0xC2, 0, 0, 0, 0, 6, 0, 8, 0, 0x53, 0x63, 61
		 * 0x6E, 0x69, 0x61, 0x2D, 0x38, (byte) 0xBB, 0, 0, 0, 0, 7, 0, 8, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x39, (byte) 0xC0, 0, 0, 0, 0, 8, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 69
		 * 0x61, 0x2D, 0x31, 0x30, (byte) 0xC3, 0, 0, 0, 0, 9, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x31, (byte) 0xBB, 0, 0, 0, 0, 0x0A, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 69
		 * 0x61, 0x2D, 0x31, 0x32, (byte) 0xAB, 0, 0, 0, 0, 0x0B, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x33, (byte) 0xC7, 0, 0, 0, 0, 0x0C, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 69
		 * 0x61, 0x2D, 0x31, 0x34, (byte) 0xB9, 0, 0, 0, 0, 0x0D, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x35, (byte) 0xAE, 0, 0, 0, 0, 0x0E, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 69
		 * 0x61, 0x2D, 0x31, 0x36, (byte) 0xB6, 0, 0, 0, 0, 0x0F, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x37, (byte) 0xDB, 0, 0, 0, 0, 0x10, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 69
		 * 0x61, 0x2D, 0x31, 0x38, (byte) 0xC7, 0, 0, 0, 0, 0x11, 0, 9, 0, 0x53, 0x63, 0x61, 0x6E, 0x69, 0x61, 0x2D, 0x31, 0x39, (byte) 0xEF, 0, 0, 0, 0, 0x12, 00
		 */

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write(serverIndex);
		mplew.writeMapleAsciiString(serverName);
		mplew.write(3); // 1: E 2: N 3: H
		// mplew.writeShort(0);

		mplew.writeMapleAsciiString("");
		mplew.write(0x64); // rate modifier, don't ask O.O!

		mplew.write(0x0); // event xp * 2.6 O.O!

		mplew.write(0x64); // rate modifier, don't ask O.O!

		mplew.write(0x0); // drop rate * 2.6

		mplew.write(0x0);
		int lastChannel = 1;
		Set<Integer> channels = channelLoad.keySet();
		for (int i = 30; i > 0; i--) {
			if (channels.contains(i)) {
				lastChannel = i;
				break;
			}
		}
		mplew.write(lastChannel);

		int load;
		for (int i = 1; i <= lastChannel; i++) {
			if (channels.contains(i)) {
				load = channelLoad.get(i);
			} else {
				load = 1200;
			}
			mplew.writeMapleAsciiString(serverName + "-" + i);
			mplew.writeInt(load);
			mplew.write(serverIndex);
			mplew.writeShort(i - 1);
		}
		mplew.writeShort(0); // ver 0.56

		return mplew.getPacket();
	}

	/**
	 * Gets a packet saying that the server list is over.
	 * 
	 * @return The end of server list packet.
	 */
	public static MaplePacket getEndOfServerList() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write((byte) 0xFF);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet detailing a server status message.
	 * 
	 * Possible values for <code>status</code>:<br>
	 * 0 - Normal<br>
	 * 1 - Highly populated<br>
	 * 2 - Full
	 * 
	 * @param status The server status.
	 * @return The server status packet.
	 */
	public static MaplePacket getServerStatus(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERSTATUS.getValue());
		mplew.writeShort(status);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client the IP of the channel server.
	 * 
	 * @param inetAddr The InetAddress of the requested channel server.
	 * @param port The port the channel is on.
	 * @param clientId The ID of the client.
	 * @return The server IP packet.
	 */
	public static MaplePacket getServerIP(InetAddress inetAddr, int port, int clientId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVER_IP.getValue());
		mplew.writeShort(0);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		// 0x0x13, = numchannels?
		mplew.writeInt(clientId); // this gets repeated to the channel server
		// leos.write(new byte[] { (byte) 0x13, (byte) 0x37, 0x42, 1, 0, 0, 0,
		// 0, 0 });

		mplew.write(new byte[]{0, 0, 0, 0, 0});
		// 0x0D, 0, 0, 0, 0x3F, (byte) 0xFB, (byte) 0xD9, 0x0D, (byte) 0x8A, 0x21, (byte) 0xCB, (byte) 0xA8, 0x13, 0, 0, 0, 0, 0, 00
		// ....?....!.........
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client the IP of the new channel.
	 * 
	 * @param inetAddr The InetAddress of the requested channel server.
	 * @param port The port the channel is on.
	 * @return The server IP packet.
	 */
	public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL.getValue());
		mplew.write(1);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet with a list of characters.
	 * 
	 * @param c The MapleClient to load characters of.
	 * @param serverId The ID of the server requested.
	 * @return The character list packet.
	 */
	public static MaplePacket getCharList(MapleClient c, int serverId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHARLIST.getValue());
		mplew.write(0);
		List<MapleCharacter> chars = c.loadCharacters(serverId);
		mplew.write((byte) chars.size());

		for (MapleCharacter chr : chars) {
			addCharEntry(mplew, chr);
		}

		mplew.writeInt(6);

		return mplew.getPacket();
	}

	/**
	 * Adds character stats to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add the stats of.
	 */
	private static void addCharStats(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		mplew.writeInt(chr.getId()); // character id
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(chr.getName(), '\0', 13));
		mplew.write(chr.getGender()); // gender (0 = male, 1 = female)
		mplew.write(chr.getSkinColor().getId()); // skin color
		mplew.writeInt(chr.getFace()); // face
		mplew.writeInt(chr.getHair()); // hair
		mplew.writeLong(0);
		mplew.writeLong(0);
		mplew.writeLong(0);
		mplew.write(chr.getLevel()); // level
		mplew.writeShort(chr.getJob().getId()); // job
		mplew.writeShort(chr.getStr()); // str
		mplew.writeShort(chr.getDex()); // dex
		mplew.writeShort(chr.getInt()); // int
		mplew.writeShort(chr.getLuk()); // luk
		mplew.writeShort(chr.getHp()); // hp (?)
		mplew.writeShort(chr.getMaxHp()); // maxhp
		mplew.writeShort(chr.getMp()); // mp (?)
		mplew.writeShort(chr.getMaxMp()); // maxmp
		mplew.writeShort(chr.getRemainingAp()); // remaining ap
		mplew.writeShort(chr.getRemainingSp()); // remaining sp
		mplew.writeInt(chr.getExp()); // current exp
		mplew.writeShort(chr.getFame()); // fame
		mplew.writeInt(0); // Gachapon EXP (Vana says this... WTF?)
		mplew.writeInt(chr.getMapId()); // current map id
		mplew.write(chr.getInitialSpawnpoint()); // spawnpoint
		mplew.writeInt(0);
	}

	/**
	 * Adds the aesthetic aspects of a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add the looks of.
	 * @param mega Unknown
	 */
	private static void addCharLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr, boolean mega) {
		mplew.write(chr.getGender());
		mplew.write(chr.getSkinColor().getId());
		mplew.writeInt(chr.getFace());
		mplew.write(mega ? 0 : 1);
		mplew.writeInt(chr.getHair());

		MapleInventory equip = chr.getInventory(MapleInventoryType.EQUIPPED);
		// Map<Integer, Integer> equipped = new LinkedHashMap<Integer,
		// Integer>();
		Map<Byte, Integer> myEquip = new LinkedHashMap<Byte, Integer>();
		Map<Byte, Integer> maskedEquip = new LinkedHashMap<Byte, Integer>();
		for (IItem item : equip.list()) {
			byte pos = (byte) (item.getPosition() * -1);
			if (pos < 100 && myEquip.get(pos) == null) {
				myEquip.put(pos, item.getItemId());
			} else if (pos > 100 && pos != 111) {
				pos -= 100;
				if (myEquip.get(pos) != null) {
					maskedEquip.put(pos, myEquip.get(pos));
				}
				myEquip.put(pos, item.getItemId());
			} else if (myEquip.get(pos) != null) {
				maskedEquip.put(pos, item.getItemId());
			}
		}
		for (Entry<Byte, Integer> entry : myEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		mplew.write((byte) 0xFF); // end of visible itens
		// masked itens

		for (Entry<Byte, Integer> entry : maskedEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		/*
		 * for (IItem item : equip.list()) { byte pos = (byte)(item.getPosition() * -1); if (pos > 100) {
		 * mplew.write(pos - 100); mplew.writeInt(item.getItemId()); } }
		 */
		// ending markers
		mplew.write((byte) 0xFF);
		IItem cWeapon = equip.getItem((byte) -111);
		mplew.writeInt(cWeapon != null ? cWeapon.getItemId() : 0);
		for (int i = 0; i < 3; i++) {
			if (chr.getPet(i) != null) {
				mplew.writeInt(chr.getPet(i).getItemId());
			} else {
				mplew.writeInt(0);
			}
		}
	}

	/**
	 * Adds an entry for a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add.
	 */
	private static void addCharEntry(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		addCharStats(mplew, chr);
		addCharLook(mplew, chr, false);
		if (chr.getJob().isA(MapleJob.GM)) {
			mplew.write(0);
			return;
		}
		mplew.write(1); // world rank enabled

		mplew.writeInt(chr.getRank()); // world rank

		mplew.writeInt(chr.getRankMove()); // move (negative is downwards)

		mplew.writeInt(chr.getJobRank()); // job rank

		mplew.writeInt(chr.getJobRankMove()); // move (negative is downwards)

	}

	/**
	 * Adds a quest info entry for a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add quest info about.
	 */
	private static void addQuestInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		List<MapleQuestStatus> started = chr.getStartedQuests();
		mplew.writeShort(started.size());
		for (MapleQuestStatus q : started) {
			mplew.writeShort(q.getQuest().getId());
			String killStr = "";
			for (int kills : q.getMobKills().values()) {
				killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3);
			}
			mplew.writeMapleAsciiString(killStr);
		}
		List<MapleQuestStatus> completed = chr.getCompletedQuests();
		mplew.writeShort(completed.size());
		for (MapleQuestStatus q : completed) {
			mplew.writeShort(q.getQuest().getId());
			mplew.writeLong(q.getCompletionTime());
		}
	}

	/**
	 * Gets character info for a character.
	 * 
	 * @param chr The character to get info about.
	 * @return The character info packet.
	 */
	public static MaplePacket getCharInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49
		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.write(1);
		mplew.write(1);
		mplew.writeShort(0);
		Random rand = new Random();
		mplew.writeInt(rand.nextInt());
		mplew.writeInt(rand.nextInt());
		mplew.writeInt(rand.nextInt());
		mplew.writeLong(-1);
		addCharStats(mplew, chr);
		mplew.write(chr.getBuddylist().getCapacity());
		addInventoryInfo(mplew, chr);
		addSkillInfo(mplew, chr);
		addQuestInfo(mplew, chr);
		mplew.writeInt(0);
		mplew.writeInt(0);
		for (int x = 0; x < 15; x++) {
			//TeleportMaps(5)
			//VIPTeleportMaps(10)
			//NoMap->999999999->CHAR_INFO_MAGIC
			mplew.write(CHAR_INFO_MAGIC);
		}

		// Monster Book
		mplew.writeInt(0); // Cover
		mplew.write(0);
		mplew.writeShort(0); // Number of cards
		//	for (MonsterBookEntry card : monsterBook) {
		//		mplew.writeShort(card.getId());
		//		mplew.write(card.getLevel);
		// }

		mplew.writeShort(0);
		mplew.writeShort(0); // Number of PQs
		mplew.writeShort(0);

		mplew.writeLong(getTime((long) System.currentTimeMillis()));

		return mplew.getPacket();
	}

	/**
	 * Gets an empty stat update.
	 * 
	 * @return The empy stat update packet.
	 */
	public static MaplePacket enableActions() {
		return updatePlayerStats(EMPTY_STATUPDATE, true);
	}

	/**
	 * Gets an update for specified stats.
	 * 
	 * @param stats The stats to update.
	 * @return The stat update packet.
	 */
	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats) {
		return updatePlayerStats(stats, false);
	}

	/**
	 * Gets an update for specified stats.
	 * 
	 * @param stats The list of stats to update.
	 * @param itemReaction Result of an item reaction(?)
	 * @param pet Result of spawning a pet(?)
	 * @return The stat update packet.
	 */
	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
		if (itemReaction) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		int updateMask = 0;
		for (Pair<MapleStat, Integer> statupdate : stats) {
			updateMask |= statupdate.getLeft().getValue();
		}
		List<Pair<MapleStat, Integer>> mystats = stats;
		if (mystats.size() > 1) {
			Collections.sort(mystats, new Comparator<Pair<MapleStat, Integer>>() {
				@Override
				public int compare(Pair<MapleStat, Integer> o1, Pair<MapleStat, Integer> o2) {
					int val1 = o1.getLeft().getValue();
					int val2 = o2.getLeft().getValue();
					return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
				}
			});
		}
		mplew.writeInt(updateMask);
		for (Pair<MapleStat, Integer> statupdate : mystats) {
			if (statupdate.getLeft().getValue() >= 1) {
				if (statupdate.getLeft().getValue() == 0x1) {
					mplew.writeShort(statupdate.getRight().shortValue());
				} else if (statupdate.getLeft().getValue() <= 0x4) {
					mplew.writeInt(statupdate.getRight());
				} else if (statupdate.getLeft().getValue() < 0x20) {
					mplew.write(statupdate.getRight().shortValue());
				} else if (statupdate.getLeft().getValue() < (byte) 0xFFFF) {
					mplew.writeShort(statupdate.getRight().shortValue());
				} else {
					mplew.writeInt(statupdate.getRight().intValue());
				}
			}
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to change maps.
	 * 
	 * @param to The <code>MapleMap</code> to warp to.
	 * @param spawnPoint The spawn portal number to spawn at.
	 * @param chr The character warping to <code>to</code>
	 * @return The map change packet.
	 */
	public static MaplePacket getWarpToMap(MapleMap to, int spawnPoint, MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49

		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.writeShort(0x2);
		mplew.writeShort(0);
		mplew.writeInt(to.getId());
		mplew.write(spawnPoint);
		mplew.writeShort(chr.getHp()); // hp (???)

		mplew.write(0);
		long questMask = 0x1ffffffffffffffL;
		mplew.writeLong(questMask);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a portal.
	 * 
	 * @param townId The ID of the town the portal goes to.
	 * @param targetId The ID of the target.
	 * @param pos Where to put the portal.
	 * @return The portal spawn packet.
	 */
	public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		if (pos != null) {
			mplew.writeShort(pos.x);
			mplew.writeShort(pos.y);
		}
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a door.
	 * 
	 * @param oid The door's object ID.
	 * @param pos The position of the door.
	 * @param town
	 * @return The remove door packet.
	 */
	public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
		// [(byte) 0xD3, 00] [01] [(byte) 0x93, (byte) 0xAC, 0, 00] [0x6B, 05] [0x37, 03]
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_DOOR.getValue());

		mplew.write(town ? 1 : 0);
		mplew.writeInt(oid);
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet to remove a door.
	 * 
	 * @param oid The door's ID.
	 * @param town
	 * @return The remove door packet.
	 */
	public static MaplePacket removeDoor(int oid, boolean town) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (town) {
			mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
			mplew.writeInt(999999999);
			mplew.writeInt(999999999);
		} else {
			mplew.writeShort(SendPacketOpcode.REMOVE_DOOR.getValue());
			mplew.write(/*town ? 1 : */0);
			mplew.writeInt(oid);
		}
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a special map object.
	 * 
	 * @param chr The MapleCharacter who spawned the object.
	 * @param skill The skill used.
	 * @param skillLevel The level of the skill used.
	 * @param pos Where the object was spawned.
	 * @param movementType Movement type of the object.
	 * @param animated Animated spawn?
	 * @return The spawn packet for the map object.
	 */
	public static MaplePacket spawnSpecialMapObject(MapleSummon summon, int skillLevel, boolean animated) {
		// 0x72, 0, 0x29, 0x1D, 2, 0, (byte) 0xFD, (byte) 0xFE, 0x30, 0, 0x19, 0x7D, (byte) 0xFF, (byte) 0xBA, 0, 4, 1, 0, 3, 1, 00
		// (byte) 0x85, 0, [0x6A, 0x4D, 0x27, 00] [0x35, 0x1F, 0, 00] [0x2D, 0x5D, 0x20, 00] [0C] [(byte) 0x8C, 16] [(byte) 0xCA, 01] [03] [00] [00] [01] [01] [00]

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());

		mplew.writeInt(summon.getOwner().getId());
		mplew.writeInt(summon.getObjectId()); // Supposed to be Object ID, but this works too! <3

		mplew.writeInt(summon.getSkill());
		mplew.write(skillLevel);
		mplew.writeShort(summon.getPosition().x);
		mplew.writeShort(summon.getPosition().y);
		mplew.write(3); // test

		mplew.write(0); // test

		mplew.write(0); // test

		mplew.write(summon.getMovementType().getValue()); // 0 = don't move, 1 = follow
		// (4th mage summons?), 2/4 =
		// only tele follow, 3 = bird
		// follow

		mplew.write(1); // 0 and the summon can't attack - but puppets don't
		// attack with 1 either ^.-

		mplew.write(animated ? 0 : 1);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet to remove a special map object.
	 * 
	 * @param chr The MapleCharacter who removed the object.
	 * @param skill The skill used to create the object.
	 * @param animated Animated removal?
	 * @return The packet removing the object.
	 */
	public static MaplePacket removeSpecialMapObject(MapleSummon summon, boolean animated) {
		// [(byte) 0x86, 00] [0x6A, 0x4D, 0x27, 00] 0x33, 0x1F, 0, 0, 02
		// (byte) 0x92, 0, 0x36, 0x1F, 0, 0, 0x0F, 0x65, (byte) 0x85, 1, (byte) 0x84, 2, 6, 0x46, 0x28, 0, 6, (byte) 0x81, 2, 1, (byte) 0xD9, 0, (byte) 0xBD, (byte) 0xFB, (byte) 0xD9, 0, (byte) 0xBD, (byte) 0xFB, 0x38, 4, 0x2F, 0x21, 0, 0, 0x10, (byte) 0xC1, 0x2A, 0, 6, 0, 6, 1, 0, 1, (byte) 0xBD, (byte) 0xFB, (byte) 0xFC, 0, (byte) 0xBD, (byte) 0xFB, 0x6A, 4, (byte) 0x88, 0x1D, 0, 0, 0x7D, 1, (byte) 0xAF, FB
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT.getValue());

		mplew.writeInt(summon.getOwner().getId());
		mplew.writeInt(summon.getObjectId());

		mplew.write(animated ? 4 : 1); // ?

		return mplew.getPacket();
	}

	private static void addSkillInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		Map<ISkill, MapleCharacter.SkillEntry> skills = chr.getSkills();
		mplew.writeShort(skills.size());
		for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
			mplew.writeInt(skill.getKey().getId());
			mplew.writeInt(skill.getValue().skillevel);
			if (skill.getKey().isFourthJob()) {
				mplew.writeInt(skill.getValue().masterlevel);
			}
		}
		mplew.writeShort(0);
	}

	private static void addInventoryInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		mplew.writeInt(chr.getMeso());
		mplew.write(chr.getEquipSlots());
		mplew.write(chr.getUseSlots());
		mplew.write(chr.getSetupSlots());
		mplew.write(chr.getEtcSlots());
		mplew.write(chr.getCashSlots());

		MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);

		for (IItem item : iv.list()) {
			if (item.getPosition() > -100) {
				addItemInfo(mplew, item);
			}
		}
		mplew.write(0);

		for (IItem item : iv.list()) {
			if (item.getPosition() < -100) {
				addItemInfo(mplew, item);
			}
		}
		mplew.write(0);

		iv = chr.getInventory(MapleInventoryType.EQUIP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0);

		iv = chr.getInventory(MapleInventoryType.USE);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0);

		iv = chr.getInventory(MapleInventoryType.SETUP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0);

		iv = chr.getInventory(MapleInventoryType.ETC);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0);

		iv = chr.getInventory(MapleInventoryType.CASH);
		for (IItem item : iv.list()) {
			if (item.getPetId() > -1) {
				addPetInfo(mplew, MaplePet.loadFromDb(item.getItemId(), item.getPosition(), item.getPetId()));
			} else {
				addItemInfo(mplew, item);
			}
		}
		mplew.write(0);
	}

	/**
	 * Adds info about an item to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param item The item to write info about.
	 */
	protected static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item) {
		addItemInfo(mplew, item, false, false, false);
	}

	/**
	 * Adds expiration time info to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param time The expiration time.
	 * @param showexpirationtime Show the expiration time?
	 */
	private static void addExpirationTime(MaplePacketLittleEndianWriter mplew, long time, boolean showexpirationtime) {
		mplew.writeInt(KoreanDateUtil.getItemTimestamp(time));
		mplew.write(showexpirationtime ? 1 : 2);
	}

	/**
	 * Adds item info to existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param item The item to add info about.
	 * @param zeroPosition Is the position zero?
	 * @param leaveOut Leave out the item if position is zero?
	 */
	private static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, boolean zeroPosition, boolean leaveOut, boolean shortSlot) {
		// 1, // 1, // 0x41, (byte) 0xBF, 0x0F, 0, // 0, //
		// 0, (byte) 0x80, 5, (byte) 0xBB, 0x46, (byte) 0xE6, 0x17, 2, //


		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		IEquip equip = null;
		if (item.getType() == IItem.EQUIP) {
			equip = (IEquip) item;
		}
		if (shortSlot) {
			mplew.writeShort(item.getPosition());
		} else if (zeroPosition) {
			if (!leaveOut) {
				mplew.write(0);
			}
		} else {
			int slot = Math.abs(item.getPosition());
			if (slot > 100) {
				slot -= 100;
			}
			mplew.write(slot);
		}

		mplew.write(item.getType());
		mplew.writeInt(item.getItemId());
		mplew.write(0);

		mplew.write(0);
		mplew.write(ITEM_MAGIC);
		addExpirationTime(mplew, 0, false);

		if (item.getType() == IItem.EQUIP) {
			mplew.write(equip.getUpgradeSlots());
			mplew.write(equip.getLevel());
			mplew.writeShort(equip.getStr());
			mplew.writeShort(equip.getDex());
			mplew.writeShort(equip.getInt());
			mplew.writeShort(equip.getLuk());
			mplew.writeShort(equip.getHp());
			mplew.writeShort(equip.getMp());
			mplew.writeShort(equip.getWatk());
			mplew.writeShort(equip.getMatk());
			mplew.writeShort(equip.getWdef());
			mplew.writeShort(equip.getMdef());
			mplew.writeShort(equip.getAcc());
			mplew.writeShort(equip.getAvoid());
			mplew.writeShort(equip.getHands());
			mplew.writeShort(equip.getSpeed());
			mplew.writeShort(equip.getJump());
			mplew.writeMapleAsciiString(equip.getOwner());
			mplew.writeShort(0); // Flags - Locked/Cold/Spikes/etc
			mplew.write(0);
			mplew.write(0); // Item Level
			mplew.writeShort(0);
			mplew.writeShort(0); // Item EXP
			mplew.writeInt(equip.getHammers());
			mplew.writeLong(-1);
			mplew.write(new byte[] { 0, 0x40, (byte) (byte) 0xE0, (byte) (byte) 0xFD, 0x3B, 0x37, 0x4F, 1 });
			mplew.writeInt(-1);
		} else {
			mplew.writeShort(item.getQuantity());
			mplew.writeMapleAsciiString(item.getOwner());
			mplew.writeShort(0); // Flags i.e. locked

			if (ii.isThrowingStar(item.getItemId()) || ii.isBullet(item.getItemId())) {
				mplew.writeLong(0);
			}
		}
	}

	private static void addPetInfo(MaplePacketLittleEndianWriter mplew, MaplePet pet) {
		addPetInfo(mplew, pet, false);
	}

	private static void addPetInfo(MaplePacketLittleEndianWriter mplew, MaplePet pet, boolean zeroPosition) {
		mplew.write(zeroPosition ? 0 : pet.getPosition());
		mplew.write(3);
		mplew.writeInt(pet.getItemId());
		mplew.write(1);
		mplew.writeInt(pet.getUniqueId());
		mplew.writeInt(0);
		mplew.write(0);
		mplew.write(ITEM_MAGIC);
		addExpirationTime(mplew, 0, false);
		String petname = pet.getName();
		if (petname.length() > 13) {
			petname = petname.substring(0, 13);
		}
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(petname, '\0', 13));
		mplew.write(pet.getLevel());
		mplew.writeShort(pet.getCloseness());
		mplew.write(pet.getFullness());
		mplew.writeLong(getKoreanTimestamp((long) (System.currentTimeMillis() * 1.2)));
		mplew.writeInt(0);
		mplew.writeInt(0);
	}

	/**
	 * Gets the response to a relog request.
	 * 
	 * @return The relog response packet.
	 */
	public static MaplePacket getRelogResponse() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.RELOG_RESPONSE.getValue());
		mplew.write(1);
		return mplew.getPacket();
	}

	/**
	 * Gets a server message packet.
	 * 
	 * @param message The message to convey.
	 * @return The server message packet.
	 */
	public static MaplePacket serverMessage(String message) {
		return serverMessage(4, 0, message, true);
	}

	/**
	 * Gets a server notice packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 
	 * @param type The type of the notice.
	 * @param message The message to convey.
	 * @return The server notice packet.
	 */
	public static MaplePacket serverNotice(int type, String message) {
		return serverMessage(type, 0, message, false);
	}

	/**
	 * Gets a server notice packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 
	 * @param type The type of the notice.
	 * @param channel The channel this notice was sent on.
	 * @param message The message to convey.
	 * @return The server notice packet.
	 */
	public static MaplePacket serverNotice(int type, int channel, String message) {
		return serverMessage(type, channel, message, false);
	}

	/**
	 * Gets a server message packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 8: Item Megaphone
	 * 
	 * @param type The type of the notice.
	 * @param channel The channel this notice was sent on.
	 * @param message The message to convey.
	 * @param servermessage Is this a scrolling ticker?
	 * @return The server notice packet.
	 */
	private static MaplePacket serverMessage(int type, int channel, String message, boolean servermessage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 0x41, 0, //
		// 8, //
		// 0x48, 0, // 0x70, 0x6F, 0x6F, 0x70, 0x6F, 0x6F, 0x31, 0x32, 0x33, 0x20, 0x3A, 0x20, 0x3C, 0x3C, 0x53, 0x65, 0x6C, 0x6C, 0x69, 0x6E, 0x67, 0x20, 0x74, 0x68, 0x65, 0x73, 0x65, 0x20, 0x57, 0x68, 0x69, 0x73, 0x70, 0x20, 0x6F, 0x66, 0x66, 0x65, 0x72, 0x73, 0x20, 0x31, 0x38, 0x30, 0x6D, 0x20, 0x48, 0x2F, 0x4F, 0x20, 0x53, 0x68, 0x65, 0x72, 0x20, 0x69, 0x73, 0x20, 0x6C, 0x61, 0x7A, 0x79, 0x21, 0x21, 0x20, 0x46, 0x61, 0x74, 0x61, 0x6C, 0x3C, 0x33, //
		// 0x11, //
		// 1, //

		mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue()); // 0.47:
		// 0x37,
		// unchanged

		mplew.write(type);
		if (servermessage) {
			mplew.write(1);
		}
		mplew.writeMapleAsciiString(message);

		if (type == 3) {
			mplew.write(channel - 1);
			mplew.write(0);
		} else if (type == 6) {
			mplew.writeInt(0);
		} else if (type == 8) {
			mplew.write(channel - 1);
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a server message packet.
	 *
	 * Possible values for <code>type</code>:<br />
	 * 0: Megaphone<br />
	 * 1: Supermegaphone<br />
	 *
	 *
	 * @param type The type of the notice.
	 * @param channel The channel this notice was sent on.
	 * @param message The message to convey.
	 * @param servermessage Is this a scrolling ticker?
	 * @return The server notice packet.
	 */
	public static MaplePacket getMegaphone(Items.MegaPhoneType type, int channel, String message, IItem item, boolean showEar) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
		mplew.write(type.getValue());
		mplew.writeMapleAsciiString(message);
		if (type == Items.MegaPhoneType.SUPERMEGAPHONE) {
			mplew.write(channel - 1);
			mplew.write(showEar ? 1 : 0);
		} else if (type == Items.MegaPhoneType.ITEMMEGAPHONE) {
			mplew.write(channel - 1);
			mplew.write(showEar ? 1 : 0);
			if (item != null) {
				addItemInfo(mplew, item);
			} else {
				mplew.write(0);
			}
		}
		return mplew.getPacket();
	}

	public static MaplePacket getTripleMegaphone(int channel, String[] messages, boolean showEar) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
		mplew.write(10);
		if (messages[0] != null) {
            mplew.writeMapleAsciiString(messages[0]);
        }
        mplew.write(messages.length);
        for (int i = 1; i < messages.length; i++) {
            if (messages[i] != null) {
                mplew.writeMapleAsciiString(messages[i]);
            }
        }
        for (int i = 0; i < 10; i++) {
            mplew.write(channel - 1);
		}
        mplew.write(showEar ? 1 : 0);
        mplew.write(1);
        return mplew.getPacket();
	}

	/**
	 * Gets an avatar megaphone packet.
	 * 
	 * @param chr The character using the avatar megaphone.
	 * @param channel The channel the character is on.
	 * @param itemId The ID of the avatar-mega.
	 * @param message The message that is sent.
	 * @return The avatar mega packet.
	 */
	public static MaplePacket getAvatarMega(MapleCharacter chr, int channel, int itemId, List<String> message) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
		mplew.writeInt(itemId);
		mplew.writeMapleAsciiString(chr.getName());
		for (String s : message) {
			mplew.writeMapleAsciiString(s);
		}
		mplew.writeInt(channel - 1); // channel

		mplew.write(0);
		addCharLook(mplew, chr, true);

		return mplew.getPacket();
	}

	/**
	 * Gets a NPC spawn packet.
	 * 
	 * @param life The NPC to spawn.
	 * @param requestController Does the NPC want a controller?
	 * @return The NPC spawn packet.
	 */
	public static MaplePacket spawnNPC(MapleNPC life, boolean requestController) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// Request
		// (byte) 0xE5, 0, [01] [0x7D, 1, 0, 00] [0x24, 0x71, 0x0F, 00] [0x2B, 0D] [(byte) 0x8C, FF] [01] [0x3F, 00] [(byte) 0xF9, 0C] [0x5D, 0D] [01]
		// Spawn
		// (byte) 0xE3, 0, [0x7E, 1, 0, 00] [0x44, (byte) 0xDB, (byte) 0x8A, 00] [0, 02] [(byte) 0x9A, 00] [01] [0x77, 00] [(byte) 0xCE, 01] [0x32, 02] [01]
		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER.getValue());
			mplew.write(1); // ?

		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
		}
		mplew.writeInt(life.getObjectId());
		mplew.writeInt(life.getId());
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getCy());
		mplew.write(life.getF() == 1 ? 0 : 1); //Facing Left

		mplew.writeShort(life.getFh());
		mplew.writeShort(life.getRx0());
		mplew.writeShort(life.getRx1());

		mplew.write(1);

		return mplew.getPacket();
	}

	/**
	 * Gets a spawn monster packet.
	 * 
	 * @param life The monster to spawn.
	 * @param newSpawn Is it a new spawn?
	 * @return The spawn monster packet.
	 */
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn) {
		return spawnMonsterInternal(life, false, newSpawn, false, 0);
	}

	/**
	 * Gets a spawn monster packet.
	 * 
	 * @param life The monster to spawn.
	 * @param newSpawn Is it a new spawn?
	 * @param effect The spawn effect.
	 * @return The spawn monster packet.
	 */
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn, int effect) {
		return spawnMonsterInternal(life, false, newSpawn, false, effect);
	}

	/**
	 * Gets a control monster packet.
	 * 
	 * @param life The monster to give control to.
	 * @param newSpawn Is it a new spawn?
	 * @param aggro Aggressive monster?
	 * @return The monster control packet.
	 */
	public static MaplePacket controlMonster(MapleMonster life, boolean newSpawn, boolean aggro) {
		return spawnMonsterInternal(life, true, newSpawn, aggro, 0);
	}

	/**
	 * Internal function to handler monster spawning and controlling.
	 * 
	 * @param life The mob to perform operations with.
	 * @param requestController Requesting control of mob?
	 * @param newSpawn New spawn (fade in?)
	 * @param aggro Aggressive mob?
	 * @param effect The spawn effect to use.
	 * @return The spawn/control packet.
	 */
	private static MaplePacket spawnMonsterInternal(MapleMonster life, boolean requestController, boolean newSpawn,
			boolean aggro, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
			if (aggro) {
				mplew.write(2);
			} else {
				mplew.write(1);
			}
		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
		}
		mplew.writeInt(life.getObjectId());
		mplew.write(5);

		mplew.writeInt(life.getId());
		mplew.writeShort(0);
		mplew.write(0);
		mplew.write(8);
		mplew.writeInt(0);

		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		mplew.write(life.getStance()); // or 5? o.O"

		mplew.writeShort(0); // seems to be left and right
		// restriction...

		mplew.writeShort(life.getFh());

		if (effect > 0) {
			mplew.write(effect);
			mplew.write(0);
			mplew.writeShort(0);
		}

		if (newSpawn) {
			mplew.writeShort(-2);
		} else {
			mplew.writeShort(-1);
		}

		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Handles spawning monsters that spawn after another is killed
	 * @param life The mob to spawn
	 * @param parent The OID of the parent mob
	 * @return The packet to spawn the mob
	 */
	public static MaplePacket spawnRevives(MapleMonster life, int parent) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
		mplew.writeInt(life.getObjectId());
		mplew.write(1);
		mplew.writeInt(life.getId());
		mplew.write(0); // Status

		mplew.writeShort(0);
		mplew.write(8);
		mplew.writeInt(0);
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		mplew.write(life.getStance());
		mplew.writeShort(life.getFh());
		mplew.writeShort(life.getStartFh());
		mplew.write((byte) 0xFD); // FD

		mplew.writeInt(parent); // oid of the mob that spawned it

		mplew.writeShort(-1);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Handles monsters not being targettable, such as Zakum's first body.
	 * @param life The mob to spawn as non-targettable.
	 * @param effect The effect to show when spawning.
	 * @return The packet to spawn the mob as non-targettable.
	 */
	public static MaplePacket spawnFakeMonster(MapleMonster life, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());

		mplew.write(1);
		mplew.writeInt(life.getObjectId());
		mplew.write(5);
		mplew.writeInt(life.getId());
		mplew.writeInt(0);
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		mplew.write(life.getStance());
		mplew.writeShort(life.getStartFh());
		mplew.writeShort(life.getFh());

		if (effect > 0) {
			mplew.write(effect);
			mplew.write(0);
			mplew.writeShort(0);
		}

		mplew.writeShort(-2);

		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Makes a monster previously spawned as non-targettable, targettable.
	 * @param life The mob to make targettable.
	 * @return The packet to make the mob targettable.
	 */
	public static MaplePacket makeMonsterReal(MapleMonster life) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());

		mplew.writeInt(life.getObjectId());
		mplew.write(5);
		mplew.writeInt(life.getId());
		mplew.writeInt(0);
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		mplew.write(life.getStance());
		mplew.writeShort(life.getStartFh());
		mplew.writeShort(life.getFh());
		mplew.writeShort(-1);

		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Gets a stop control monster packet.
	 * 
	 * @param oid The ObjectID of the monster to stop controlling.
	 * @return The stop control monster packet.
	 */
	public static MaplePacket stopControllingMonster(int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
		mplew.write(0);
		mplew.writeInt(oid);

		return mplew.getPacket();
	}

	/**
	 * Gets a response to a move monster packet.
	 * 
	 * @param objectid The ObjectID of the monster being moved.
	 * @param moveid The movement ID.
	 * @param currentMp The current MP of the monster.
	 * @param useSkills Can the monster use skills?
	 * @return The move response packet.
	 */
	public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills) {
		return moveMonsterResponse(objectid, moveid, currentMp, useSkills, 0, 0);
	}

	/**
	 * Gets a response to a move monster packet.
	 * 
	 * @param objectid The ObjectID of the monster being moved.
	 * @param moveid The movement ID.
	 * @param currentMp The current MP of the monster.
	 * @param useSkills Can the monster use skills?
	 * @param skillId The skill ID for the monster to use.
	 * @param skillLevel The level of the skill to use.
	 * @return The move response packet.
	 */
	public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills, int skillId, int skillLevel) {
		// (byte) 0xA1, 0, 0x18, (byte) 0xDC, 0x41, 0, 1, 0, 0, 0x1E, 0, 0, 00
		// (byte) 0xA1, 0, 0x22, 0x22, 0x22, 0x22, 1, 0, 0, 0, 0, 0, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE.getValue());
		mplew.writeInt(objectid);
		mplew.writeShort(moveid);
		mplew.write(useSkills ? 1 : 0);
		mplew.writeShort(currentMp);
		mplew.write(skillId);
		mplew.write(skillLevel);

		return mplew.getPacket();
	}

	/**
	 * Gets a general chat packet.
	 * 
	 * @param cidfrom The character ID who sent the chat.
	 * @param text The text of the chat.
	 * @return The general chat packet.
	 */
	public static MaplePacket getChatText(int cidfrom, String text, boolean whiteBG, int show) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHATTEXT.getValue());
		mplew.writeInt(cidfrom);
		mplew.write(whiteBG ? 1 : 0);
		mplew.writeMapleAsciiString(text);
		mplew.write(show);

		return mplew.getPacket();
	}

	/**
	 * For testing only! Gets a packet from a hexadecimal string.
	 * 
	 * @param hex The hexadecimal packet to create.
	 * @return The MaplePacket representing the hex string.
	 */
	public static MaplePacket getPacketFromHexString(String hex) {
		byte[] b = HexTool.getByteArrayFromHexString(hex);
		return new ByteArrayMaplePacket(b);
	}

	/**
	 * Gets a packet telling the client to show an EXP increase.
	 * 
	 * @param gain The amount of EXP gained.
	 * @param inChat In the chat box?
	 * @param white White text or yellow?
	 * @return The exp gained packet.
	 */
	public static MaplePacket getShowExpGain(int gain, boolean inChat, boolean white) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 0x20, 0, 3, 1, 0x0A, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 00
		// 0x24, 0, 3, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 00
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(3);
		mplew.write(white ? 1 : 0);
		mplew.writeInt(gain);
		mplew.writeInt(inChat ? 1 : 0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		if (inChat) {
			mplew.write(0);
		}
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show a meso gain.
	 * 
	 * @param gain How many mesos gained.
	 * @return The meso gain packet.
	 */
	public static MaplePacket getShowMesoGain(int gain) {
		return getShowMesoGain(gain, false);
	}

	/**
	 * Gets a packet telling the client to show a meso gain.
	 * 
	 * @param gain How many mesos gained.
	 * @param inChat Show in the chat window?
	 * @return The meso gain packet.
	 */
	public static MaplePacket getShowMesoGain(int gain, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		if (!inChat) {
			mplew.write(0);
			mplew.write(1);
		} else {
			mplew.write(5);
		}
		mplew.writeInt(gain);
		mplew.writeShort(0); // inet cafe meso gain ?.o

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show a item gain.
	 * 
	 * @param itemId The ID of the item gained.
	 * @param quantity How many items gained.
	 * @return The item gain packet.
	 */
	public static MaplePacket getShowItemGain(int itemId, short quantity) {
		return getShowItemGain(itemId, quantity, false);
	}

	/**
	 * Gets a packet telling the client to show an item gain.
	 * 
	 * @param itemId The ID of the item gained.
	 * @param quantity The number of items gained.
	 * @param inChat Show in the chat window?
	 * @return The item gain packet.
	 */
	public static MaplePacket getShowItemGain(int itemId, short quantity, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (inChat) {
			mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
			mplew.write(3);
			mplew.write(1);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
		} else {
			mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
			mplew.writeShort(0);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
			mplew.writeInt(0);
			mplew.writeInt(0);
		}
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client that a monster was killed.
	 * 
	 * @param oid The objectID of the killed monster.
	 * @param animation Show killed animation?
	 * @return The kill monster packet.
	 */
	public static MaplePacket killMonster(int oid, boolean animation) {
		// (byte) 0x9D, 0, 0x45, 0x2B, 0x67, 0, 01
		// (byte) 0xD1, 0, 5, (byte) 0x80, 0x36, 0, 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.KILL_MONSTER.getValue());
		mplew.writeInt(oid);
		mplew.write(animation ? 1 : 0);//Not a boolean, really an int type

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show mesos coming out of a map
	 * object.
	 * 
	 * @param amount The amount of mesos.
	 * @param itemoid The ObjectID of the dropped mesos.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @return The drop mesos packet.
	 */
	public static MaplePacket dropMesoFromMapObject(int amount, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, true);
	}

	/**
	 * Gets a packet telling the client to show an item coming out of a map
	 * object.
	 * 
	 * @param itemid The ID of the dropped item.
	 * @param itemoid The ObjectID of the dropped item.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @return The drop mesos packet.
	 */
	public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, false);
	}

	/**
	 * Internal function to get a packet to tell the client to drop an item onto
	 * the map.
	 * 
	 * @param itemid The ID of the item to drop.
	 * @param itemoid The ObjectID of the dropped item.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @param mesos Is the drop mesos?
	 * @return The item drop packet.
	 */
	public static MaplePacket dropItemFromMapObjectInternal(int itemid, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod, boolean mesos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT.getValue());
		mplew.write(mod);
		mplew.writeInt(itemoid);
		mplew.write(mesos ? 1 : 0);
		mplew.writeInt(itemid);
		mplew.writeInt(ownerid);
		mplew.write(0); // TODO : DROP OWNERSHIP (0 = owner, 1 = party, 2 = FFA, 3 = explosive/FFA)
		mplew.writeShort(dropto.x);
		mplew.writeShort(dropto.y);
		if (mod != 2) {
			mplew.writeInt(dropperoid);
			mplew.writeShort(dropfrom.x);
			mplew.writeShort(dropfrom.y);
		} else {
			mplew.writeInt(dropperoid);
		}
		mplew.write((byte) 0xC2);
		if (mod != 2) {
			mplew.write(1);
			mplew.write(mesos ? 1 : 0);
		}
		if (!mesos) {
			mplew.write(ITEM_MAGIC);
			addExpirationTime(mplew, System.currentTimeMillis(), false);
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a packet spawning a player as a mapobject to other clients.
	 * 
	 * @param chr The character to spawn to other clients.
	 * @return The spawn player packet.
	 */
	public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER.getValue());
		mplew.writeInt(chr.getId());
		mplew.writeMapleAsciiString(chr.getName());

		if (chr.getGuildId() <= 0) {
			mplew.writeMapleAsciiString("");
			mplew.write(new byte[6]);
		} else {
			MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
			if (gs != null) {
				mplew.writeMapleAsciiString(gs.getName());
				mplew.writeShort(gs.getLogoBG());
				mplew.write(gs.getLogoBGColor());
				mplew.writeShort(gs.getLogo());
				mplew.write(gs.getLogoColor());
			} else {
				mplew.writeMapleAsciiString("");
				mplew.write(new byte[6]);
			}
		}

		mplew.writeInt(0);
		mplew.writeInt(1016);

		if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
			mplew.writeInt(2);
		} else {
			mplew.writeInt(0);
		}

		long buffmask = 0;
		Integer buffvalue = null;

		if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null && !chr.isHidden()) {
			buffmask |= MapleBuffStat.DARKSIGHT.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
			buffmask |= MapleBuffStat.COMBO.getValue();
			buffvalue = chr.getBuffedValue(MapleBuffStat.COMBO);
		}
		if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
			buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.SOULARROW) != null) {
			buffmask |= MapleBuffStat.SOULARROW.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
			buffvalue = chr.getBuffedValue(MapleBuffStat.MORPH);
		}

		mplew.writeInt((int) ((buffmask >> 32) & 0xffffffffL));

		if (buffvalue != null) {
			if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
				mplew.writeShort(buffvalue);
			} else {
				mplew.write(buffvalue.byteValue());
			}
		}

		mplew.writeInt((int) (buffmask & 0xffffffffL));

		int CHAR_MAGIC_SPAWN = new Random().nextInt();
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeShort(0);
		mplew.write(0);

		if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
			if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) == 1004) {
				IItem mount = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18);
				mplew.writeInt(mount.getItemId());
				mplew.writeInt(1004);
				mplew.writeInt(2100000000);
			} else {
				mplew.writeInt(1932000);
				mplew.writeInt(5221006);
				mplew.writeInt(2100000000);
			}
		} else {
			mplew.writeLong(0);
			mplew.writeInt(CHAR_MAGIC_SPAWN);
		}

		mplew.writeLong(0);
		mplew.write(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeLong(0);
		mplew.writeLong(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeLong(0);
		mplew.writeInt(0);
		mplew.write(0);
		mplew.writeInt(CHAR_MAGIC_SPAWN);
		mplew.writeShort(0);
		mplew.write(0);

		mplew.writeShort(chr.getJob().getId());

		addCharLook(mplew, chr, false);

		mplew.writeInt(0);
		mplew.writeInt(chr.getItemEffect());
		mplew.writeInt(chr.getChair());
		mplew.writeShort(chr.getPosition().x);
		mplew.writeShort(chr.getPosition().y);
		mplew.write(chr.getStance());
		mplew.writeShort(0); // FH
		mplew.write(0);
		for (int i = 0; i < 3; i++) {
			MaplePet pet = chr.getPet(i);
			if (pet != null) {
				mplew.write(1);
				mplew.writeInt(pet.getItemId());
				mplew.writeMapleAsciiString(pet.getName());
				mplew.writeInt(pet.getUniqueId());
				mplew.writeInt(0);
				mplew.writeShort(pet.getPos().x);
				mplew.writeShort(pet.getPos().y);
				mplew.write(pet.getStance());
				mplew.writeInt(pet.getFh());
			}
		}

		mplew.write(0);
		mplew.writeShort(1);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeShort(0);
		return mplew.getPacket();
	}

	/**
	 * Adds a announcement box to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to add an announcement box
	 *            to.
	 * @param shop The shop to announce.
	 */
	private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, MaplePlayerShop shop) {
		// 00: no game
		// 01: omok game
		// 02: card game
		// 04: shop
		mplew.write(4);
		mplew.writeInt(shop.getObjectId()); // gameid/shopid

		mplew.writeMapleAsciiString(shop.getDescription()); // desc
		// 00: public
		// 01: private

		mplew.write(0);
		// 00: red 4x3
		// 01: green 5x4
		// 02: blue 6x5
		// omok:
		// 00: normal
		mplew.write(0);
		// first slot: 1/2/3/4
		// second slot: 1/2/3/4
		mplew.write(1);
		mplew.write(4);
		// 0: open
		// 1: in progress
		mplew.write(0);
	}

	public static MaplePacket facialExpression(MapleCharacter from, int expression) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION.getValue());

		mplew.writeInt(from.getId());
		mplew.writeInt(expression);

		return mplew.getPacket();
	}

	private static void serializeMovementList(LittleEndianWriter lew, List<LifeMovementFragment> moves) {
		lew.write(moves.size());
		for (LifeMovementFragment move : moves) {
			move.serialize(lew);
		}
	}

	public static MaplePacket movePlayer(int cid, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_PLAYER.getValue());
		mplew.writeInt(cid);

		mplew.writeInt(0);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket moveSummon(int cid, int oid, Point startPos, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_SUMMON.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(oid);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket moveMonster(int useskill, int skill, int skill_1, int skill_2, int skill_3, int oid, Point startPos,
			List<LifeMovementFragment> moves) {
		/*
		 * (byte) 0xA0, 0, (byte) 0xC8, 0, 0, 0, 0, (byte) 0xFF, 0, 0, 0, 0, 0x48, 2, 0x7D, (byte) 0xFE, 2, 0, 0x1C, 2, 0x7D, (byte) 0xFE, (byte) 0x9C, (byte) 0xFF, 0, 0, 0x2A, 0, 3, (byte) 0xBD, 1, 0, (byte) 0xDC, 1, 0x7D, FE
		 * (byte) 0x9C, (byte) 0xFF, 0, 0, 0x2B, 0, 3, 0x7B, 02
		 */
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
		// mplew.writeShort((byte) 0xA2); // 0x47, a0
		mplew.writeInt(oid);
		mplew.write(useskill);
		mplew.write(skill);
		mplew.write(skill_1);
		mplew.write(skill_2);
		mplew.write(skill_3);
		mplew.write(0);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket summonAttack(int cid, int summonSkillId, int newStance, List<SummonAttackEntry> allDamage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(newStance);
		mplew.write(allDamage.size());
		for (SummonAttackEntry attackEntry : allDamage) {
			mplew.writeInt(attackEntry.getMonsterOid()); // oid

			mplew.write(6); // who knows

			mplew.writeInt(attackEntry.getDamage()); // damage

		}

		return mplew.getPacket();
	}

	public static MaplePacket closeRangeAttack(MapleCharacter player, AttackInfo attack) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK.getValue());
		int skillLevel = player.getSkillLevel(SkillFactory.getSkill(attack.skill));
		if (attack.skill == 4211006) {
			addMesoExplosion(mplew, player.getId(), attack.skill, skillLevel, attack.stance, attack.numAttackedAndDamage, 0, attack.allDamage, attack.speed);
		} else {
			addAttackBody(mplew, player.getId(), attack, skillLevel, 0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket rangedAttack(MapleCharacter player, AttackInfo attack, int projectile) {
		// 0x7E, 0, 0x30, 0x75, 0, 0, 1, 0, (byte) 0x97, 4, 0x0A, (byte) 0xCB, 0x72, 0x1F, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.RANGED_ATTACK.getValue());
		// mplew.writeShort((byte) 0x80); // 0x47, 7E
		int skillLevel = player.getSkillLevel(SkillFactory.getSkill(attack.skill));
		addAttackBody(mplew, player.getId(), attack, skillLevel, projectile);

		mplew.writeLong(0); // This is 8 bytes, read from the received packet
		//  e.g. ((byte) 0x88, 5, 0, (byte) 0x80, (byte) 0xEB, 2, (byte) 0x8F, FF)

		return mplew.getPacket();
	}

	public static MaplePacket magicAttack(MapleCharacter player, AttackInfo attack) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK.getValue());
		int skillLevel = player.getSkillLevel(SkillFactory.getSkill(attack.skill));
		addAttackBody(mplew, player.getId(), attack, skillLevel, 0);
		if (attack.charge != -1) {
			mplew.writeInt(attack.charge);
		}

		return mplew.getPacket();
	}

	private static void addAttackBody(LittleEndianWriter lew, int cid, AttackInfo attack, int skillLevel, int projectile) {
		lew.writeInt(cid);
		lew.write(attack.numAttackedAndDamage);
		if (attack.skill > 0) {
			lew.write(skillLevel); // too low and some skills don't work (?)

			lew.writeInt(attack.skill);
		} else {
			lew.write(0);
		}
		lew.write(0);
		lew.write(attack.stance);
		lew.write(attack.speed);
		lew.write(3);
		lew.writeInt(projectile);

		for (Pair<Integer, List<Integer>> oned : attack.allDamage) {
			if (oned.getRight() != null) {
				lew.writeInt(oned.getLeft().intValue());
				lew.write(6);
				for (Integer eachd : oned.getRight()) {
					lew.writeInt(eachd.intValue());
				}
			}
		}
		lew.writeInt(attack.pos);
	}

	private static void addMesoExplosion(LittleEndianWriter lew, int cid, int skill, int skillLevel, int stance,
			int numAttackedAndDamage, int projectile,
			List<Pair<Integer, List<Integer>>> damage, int speed) {
		// 0x7A, 0, 0x6B, (byte) 0xF4, 0x0C, 0, 0x22, 0x1E, 0x3E, 0x41, 0x40, 0, 0x38, 4, 0x0A, 0, 0, 0, 0, 0x44, (byte) 0xB0, 4, 00
		// 6, 2, (byte) 0xE6, 0, 0, 0, (byte) 0xD0, 0, 0, 0, (byte) 0xF2, 0x46, 0x0E, 0, 6, 2, (byte) 0xD3, 0, 0, 0, 0x3B, 1, 00
		// 00
		// 0x7A, 0, 0x6B, (byte) 0xF4, 0x0C, 0, 0, 0x1E, 0x3E, 0x41, 0x40, 0, 0x38, 4, 0x0A, 0, 0, 0, 00
		lew.writeInt(cid);
		lew.write(numAttackedAndDamage);
		lew.write(skillLevel);
		lew.writeInt(skill);
		lew.write(0);
		lew.write(stance);
		lew.write(speed);
		lew.write(0x0A);
		lew.writeInt(projectile);

		for (Pair<Integer, List<Integer>> oned : damage) {
			if (oned.getRight() != null) {
				lew.writeInt(oned.getLeft().intValue());
				lew.write((byte) 0xFF);
				lew.write(oned.getRight().size());
				for (Integer eachd : oned.getRight()) {
					lew.writeInt(eachd.intValue());
				}
			}
		}

	}

	public static MaplePacket getNPCShop(int sid, List<MapleShopItem> items) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP.getValue());
		mplew.writeInt(sid);
		mplew.writeShort(items.size());
		for (MapleShopItem item : items) {
			mplew.writeInt(item.getItemId());
			mplew.writeInt(item.getPrice());
			if (!ii.isThrowingStar(item.getItemId()) && !ii.isBullet(item.getItemId())) {
				mplew.writeShort(1);
				mplew.writeShort(item.getMaxSlot());
			} else {
				mplew.writeLong(Double.doubleToLongBits(ii.getPrice(item.getItemId())));
				mplew.writeShort(item.getMaxSlot());
			}
		}
		return mplew.getPacket();
	}

	/**
	 * code (8 = sell, 0 = buy, 0x0x20, = due to an error the trade did not happen
	 * o.o)
	 * 
	 * @param code
	 * @return
	 */
	public static MaplePacket confirmShopTransaction(byte code) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION.getValue());
		mplew.write(code);
		return mplew.getPacket();
	}

	/*
	 * 0x19, reference 0, 1, 0, = new while adding 1, 1, 0, = add from drop 0, 1, 1, = update count 0, 1, 3, = clear slot
	 * 1, 1, 2, = move to empty slot 1, 2, 3, = move and merge 1, 2, 1, = move and merge with rest
	 */
	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item) {
		return addInventorySlot(type, item, false);
	}

	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(new byte[]{1, 0}); // add mode
		mplew.write(type.getType()); // iv type
		mplew.write(item.getPosition()); // slot id
		if (item.getPetId() != -1) {
			addPetInfo(mplew, MaplePet.loadFromDb(item.getItemId(), item.getPosition(), item.getPetId()), true);
		} else {
			addItemInfo(mplew, item, true, false, false);
		}
		return mplew.getPacket();
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item) {
		return updateInventorySlot(type, item, false);
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(new byte[]{1, 1});
		mplew.write(type.getType());
		mplew.write(item.getPosition());
		mplew.write(0);
		mplew.writeShort(item.getQuantity());

		return mplew.getPacket();
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst) {
		return moveInventoryItem(type, src, dst, (byte) -1);
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst, byte equipIndicator) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(new byte[] { 1, 1,2});
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(dst);
		if (equipIndicator != -1) {
			mplew.write(equipIndicator);
		}
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeInventoryItem(MapleInventoryType type, byte src, byte dst, short total) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(new byte[] { 1, 2,3});
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.write(1); // merge mode?

		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(total);
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeWithRestInventoryItem(MapleInventoryType type, byte src, byte dst,
			short srcQ, short dstQ) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(new byte[] { 1, 2,1});
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(srcQ);
		mplew.write(new byte[]{1});
		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(dstQ);
		return mplew.getPacket();
	}

	public static MaplePacket clearInventoryItem(MapleInventoryType type, byte slot, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(fromDrop ? 1 : 0);
		mplew.write(new byte[] { 1,3});
		mplew.write(type.getType());
		mplew.writeShort(slot);
		return mplew.getPacket();
	}

	public static MaplePacket scrolledItem(IItem scroll, IItem item, boolean destroyed) {
		// 0x18, 0, 1, 2, 3, 2, 8, 0, 3, 1, (byte) 0xF7, (byte) 0xFF, 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1); // fromdrop always true

		mplew.write(destroyed ? 2 : 3);
		mplew.write(scroll.getQuantity() > 0 ? 1 : 3);
		mplew.write(MapleInventoryType.USE.getType());
		mplew.writeShort(scroll.getPosition());
		if (scroll.getQuantity() > 0) {
			mplew.writeShort(scroll.getQuantity());
		}
		mplew.write(3);
		if (!destroyed) {
			mplew.write(MapleInventoryType.EQUIP.getType());
			mplew.writeShort(item.getPosition());
			mplew.write(0);
		}
		mplew.write(MapleInventoryType.EQUIP.getType());
		mplew.writeShort(item.getPosition());
		if (!destroyed) {
			addItemInfo(mplew, item, true, true, false);
		}
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket getScrollEffect(int chr, ScrollResult scrollSuccess, boolean legendarySpirit) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT.getValue());
		mplew.writeInt(chr);
		switch (scrollSuccess) {
			case SUCCESS:
				mplew.writeShort(1);
				mplew.writeShort(legendarySpirit ? 1 : 0);
				break;
			case FAIL:
				mplew.writeShort(0);
				mplew.writeShort(legendarySpirit ? 1 : 0);
				break;
			case CURSE:
				mplew.write(0);
				mplew.write(1);
				mplew.writeShort(legendarySpirit ? 1 : 0);
				break;
			default:
				throw new IllegalArgumentException("effect in illegal range");
		}

		return mplew.getPacket();
	}

	public static MaplePacket removePlayerFromMap(int cid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP.getValue());
		// mplew.writeShort(0x65); // 0x47, 63
		mplew.writeInt(cid);
		return mplew.getPacket();
	}

	/**
	 * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
	 * 4 - explode<br/> cid is ignored for 0 and 1
	 * 
	 * @param oid
	 * @param animation
	 * @param cid
	 * @return
	 */
	public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
		return removeItemFromMap(oid, animation, cid, false, 0);
	}

	/**
	 * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
	 * 4 - explode<br/> cid is ignored for 0 and 1.<br /><br />Flagging pet
	 * as true will make a pet pick up the item.
	 * 
	 * @param oid
	 * @param animation
	 * @param cid
	 * @param pet
	 * @param slot
	 * @return
	 */
	public static MaplePacket removeItemFromMap(int oid, int animation, int cid, boolean pet, int slot) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP.getValue());
		mplew.write(animation); // expire

		mplew.writeInt(oid);
		if (animation >= 2) {
			mplew.writeInt(cid);
			if (pet) {
				mplew.write(slot);
			}
		}
		return mplew.getPacket();
	}

	public static MaplePacket updateCharLook(MapleCharacter chr) {
		// (byte) 0x88, 0, (byte) 0x80, 0x74, 3, 0, 1, 0, 0, 0x19, 0x50, 0, 0, 0, 0x67, 0x75, 0, 0, 2, 0x34, 0x71, 0x0F, 00
		// 4, 0x59, (byte) 0xBF, 0x0F, 0, 5, (byte) 0xAB, 5, 0x10, 0, 7, (byte) 0x8C, 5B
		// 0x10, 0, 8, (byte) 0xF4, (byte) 0x82, 0x10, 0, 9, (byte) 0xE7, (byte) 0xD0, 0x10, 0, 0x0A, (byte) 0xBE, (byte) 0xA9, 0x10, 0, 0x0B, 0x0C, 5, 0x14, 0, FF
		// (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK.getValue());
		mplew.writeInt(chr.getId());
		mplew.write(1);

		addCharLook(mplew, chr, false);

		mplew.write(0);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItem(MapleInventoryType type, short src) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		mplew.write(new byte[] { 1, 1,3});
		mplew.write(type.getType());
		mplew.writeShort(src);
		if (src < 0) {
			mplew.write(1);
		}
		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItemUpdate(MapleInventoryType type, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(new byte[]{1, 1, 1});
		mplew.write(type.getType());
		mplew.writeShort(item.getPosition());
		mplew.writeShort(item.getQuantity());
		return mplew.getPacket();
	}

	public static MaplePacket damagePlayer(int skill, int monsteridfrom, int cid, int damage, int fake, int direction, boolean pgmr, int pgmr_1, boolean is_pg, int oid, int pos_x, int pos_y) {
		// (byte) 0x82, 0, 0x30, (byte) 0xC0, 0x23, 0, (byte) 0xFF, 0, 0, 0, 0, (byte) 0xB4, 0x34, 3, 0, 1, 0, 0, 0, 0, 0, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
		// mplew.writeShort((byte) 0x84); // 0x47, 82
		mplew.writeInt(cid);
		mplew.write(skill);
		mplew.writeInt(damage);
		mplew.writeInt(monsteridfrom);
		mplew.write(direction);
		if (pgmr) {
			mplew.write(pgmr_1);
			mplew.write(is_pg ? 1 : 0);
			mplew.writeInt(oid);
			mplew.write(6);
			mplew.writeShort(pos_x);
			mplew.writeShort(pos_y);
			mplew.write(0);
		} else {
			mplew.writeShort(0);
		}

		mplew.writeInt(damage);

		if (fake > 0) {
			mplew.writeInt(fake);
		}

		return mplew.getPacket();
	}

	public static MaplePacket charNameResponse(String charname, boolean nameUsed) {
		// 0x0D, 0, 0x0C, 0, 0x42, 0x6C, 0x61, 0x62, 0x6C, 0x75, 0x62, 0x62, 0x31, 0x32, 0x33, 0x34, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_NAME_RESPONSE.getValue());
		mplew.writeMapleAsciiString(charname);
		mplew.write(nameUsed ? 1 : 0);

		return mplew.getPacket();
	}

	public static MaplePacket addNewCharEntry(MapleCharacter chr, boolean worked) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());
		mplew.write(worked ? 0 : 1);
		addCharEntry(mplew, chr);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket startQuest(MapleCharacter c, short quest) {
		// [0x24, 00] [01] [0x69, 08] [1, 00] [00]
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// mplew.writeShort(0x21);
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(1);
		mplew.write(0);
		return mplew.getPacket();
	}

	/**
	 * state 0 = del ok state 0x12, = invalid bday
	 * 
	 * @param cid
	 * @param state
	 * @return
	 */
	public static MaplePacket deleteCharResponse(int cid, int state) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE.getValue());
		mplew.writeInt(cid);
		mplew.write(state);
		return mplew.getPacket();
	}

	public static MaplePacket charInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
		// mplew.writeShort(0x31);
		mplew.writeInt(chr.getId());
		mplew.write(chr.getLevel());
		mplew.writeShort(chr.getJob().getId());
		mplew.writeShort(chr.getFame());
		mplew.write(0); // heart red or gray

		if (chr.getGuildId() <= 0) {
			mplew.writeMapleAsciiString("-"); // guild

		} else {
			MapleGuildSummary gs = null;

			gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
			if (gs != null) {
				mplew.writeMapleAsciiString(gs.getName());
			} else {
				mplew.writeMapleAsciiString("-"); // guild

			}
		}

		mplew.writeMapleAsciiString(""); // Alliance

		mplew.write(0);

		MaplePet[] pets = chr.getPets();
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				mplew.write(pets[i].getUniqueId());
				mplew.writeInt(pets[i].getItemId()); // petid
				mplew.writeMapleAsciiString(pets[i].getName());
				mplew.write(pets[i].getLevel()); // pet level
				mplew.writeShort(pets[i].getCloseness()); // pet closeness
				mplew.write(pets[i].getFullness()); // pet fullness
				mplew.writeShort(0); // ??
				mplew.writeInt(0);
			}
		}

		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(1);
		mplew.writeLong(0);
		mplew.writeLong(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket forfeitQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket completeQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.write(new byte[] { 2, (byte) (byte) 0xA0, 0x67, (byte) 0xB9, (byte) 0xDA, 0x69, 0x3A, (byte) 0xC8,1});
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @param npc
	 * @param progress
	 * @return
	 */
	// frz note, 0.0x52, transition: this is only used when starting a quest and
	// seems to have no effect, is it needed?
	public static MaplePacket updateQuestInfo(MapleCharacter c, short quest, int npc, byte progress) {
		// [(byte) 0xA5, 00] [08] [0x69, 08] [(byte) 0x86, 0x71, 0x0F, 00] [0, 0, 0, 00]
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO.getValue());
		mplew.write(progress);
		mplew.writeShort(quest);
		mplew.writeInt(npc);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	private static long getLongMask(List<Pair<MapleBuffStat, Integer>> statups) {
		long mask = 0;
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mask |= statup.getLeft().getValue();
		}
		return mask;
	}

	private static long getLongMaskFromList(List<MapleBuffStat> statups) {
		long mask = 0;
		for (MapleBuffStat statup : statups) {
			mask |= statup.getValue();
		}
		return mask;
	}

	private static long getLongMaskD(List<Pair<MapleDisease, Integer>> statups) {
		long mask = 0;
		for (Pair<MapleDisease, Integer> statup : statups) {
			mask |= statup.getLeft().getValue();
		}
		return mask;
	}

	private static long getLongMaskFromListD(List<MapleDisease> statups) {
		long mask = 0;
		for (MapleDisease statup : statups) {
			mask |= statup.getValue();
		}
		return mask;
	}

	/**
	 * It is important that statups is in the correct order (see decleration
	 * order in MapleBuffStat) since this method doesn't do automagical
	 * reordering.
	 * 
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @param morph
	 * @param firstLong
	 * @return
	 */
	public static MaplePacket giveBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		long mask = getLongMask(statups);

		mplew.writeLong(0);
		mplew.writeLong(mask);
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().shortValue());
			mplew.writeInt(buffid);
			mplew.writeInt(bufflength);
		}
		mplew.writeShort(0);
		mplew.write(0);
		mplew.write(0);
		mplew.write(0);

		return mplew.getPacket();
	}

	/**
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @param morph
	 * @param firstLong
	 * @return
	 */
	public static MaplePacket giveSpeedInfusion(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups, int addedInfo) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		long mask = getLongMask(statups);

		mplew.writeLong(mask);
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeInt(statups.get(0).getRight().intValue());
		mplew.writeInt(buffid);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.writeShort(bufflength);
		mplew.writeShort(addedInfo);
		return mplew.getPacket();
	}

	/**
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @param morph
	 * @param firstLong
	 * @return
	 */
	public static MaplePacket givePirateBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		long mask = getLongMask(statups);

		mplew.writeLong(mask);
		mplew.writeLong(0);
		mplew.writeShort(0);
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().shortValue());
			mplew.writeShort(0);
			mplew.writeInt(buffid);
			mplew.writeInt(0);
			mplew.write(0);
			mplew.writeShort(bufflength);
		}
		mplew.writeShort(0);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket giveMountBuff(int skillid, int mountid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		mplew.writeLong(MapleBuffStat.MONSTER_RIDING.getValue());
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeInt(mountid);
		mplew.writeInt(skillid);
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket giveDebuff(List<Pair<MapleDisease, Integer>> statups, MobSkill skill) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		long mask = getLongMaskD(statups);

		mplew.writeLong(0);
		mplew.writeLong(mask);

		for (Pair<MapleDisease, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().shortValue());
			mplew.writeShort(skill.getSkillId());
			mplew.writeShort(skill.getSkillLevel());
			mplew.writeInt((int) skill.getDuration());
		}

		mplew.writeShort(0); // ??? wk charges have 60, here o.o

		mplew.writeShort(900);//Delay

		mplew.write(1);

		return mplew.getPacket();
	}

	public static MaplePacket giveForeignDebuff(int cid, List<Pair<MapleDisease, Integer>> statups, MobSkill skill) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		long mask = getLongMaskD(statups);

		mplew.writeLong(0);
		mplew.writeLong(mask);

		for (@SuppressWarnings("unused") Pair<MapleDisease, Integer> statup : statups) {
			//mplew.writeShort(statup.getRight().byteValue());
			mplew.writeShort(skill.getSkillId());
			mplew.writeShort(skill.getSkillLevel());
		}
		mplew.writeShort(0); // same as give_buff

		mplew.writeShort(900);//Delay

		return mplew.getPacket();
	}

	public static MaplePacket cancelForeignDebuff(int cid, List<MapleDisease> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		long mask = getLongMaskFromListD(statups);

		mplew.writeLong(0);
		mplew.writeLong(mask);

		return mplew.getPacket();
	}

	public static MaplePacket showMountBuff(int cid, int skillid, int mountid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);
		mplew.writeLong(MapleBuffStat.MONSTER_RIDING.getValue());
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeInt(mountid);
		mplew.writeInt(skillid);
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket showPirateBuff(int cid, int skillid, int time, List<Pair<MapleBuffStat, Integer>> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);
		long mask = getLongMask(statups);
		mplew.writeLong(mask);
		mplew.writeLong(0);
		mplew.writeShort(0);
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight());
			mplew.writeShort(0);
			mplew.writeInt(skillid);
			mplew.writeInt(0);
			mplew.write(0);
			mplew.writeShort(time);
		}
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket showSpeedInfusion(int cid, int skillid, int time, List<Pair<MapleBuffStat, Integer>> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);
		long mask = getLongMask(statups);
		mplew.writeLong(mask);
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeInt(statups.get(0).getRight());
		mplew.writeInt(skillid);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.writeShort(time);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket giveForeignBuff(int cid, List<Pair<MapleBuffStat, Integer>> statups, boolean morph) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);
		long mask = getLongMask(statups);
		mplew.writeLong(0);
		mplew.writeLong(mask);

		for (Pair<MapleBuffStat, Integer> statup : statups) {
			if (morph) {
				mplew.writeInt(statup.getRight());
			} else {
				mplew.writeShort(statup.getRight());
			}
		}

		mplew.writeShort(0);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);
		long mask = getLongMaskFromList(statups);
		mplew.writeLong(isFirstLong(statups) ? mask : 0);
		mplew.writeLong(isFirstLong(statups) ? 0 : mask);

		return mplew.getPacket();
	}

	public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());

		long mask = getLongMaskFromList(statups);

		mplew.writeLong(isFirstLong(statups) ? mask : 0);
		mplew.writeLong(isFirstLong(statups) ? 0 : mask);
		mplew.write(3); // wtf?

		return mplew.getPacket();
	}

	private static boolean isFirstLong(List<MapleBuffStat> statups) {
		for (MapleBuffStat stat : statups) {
			if (stat.equals(MapleBuffStat.DASH) ||
					stat.equals(MapleBuffStat.DASH2) ||
					stat.equals(MapleBuffStat.SPEED_INFUSION) ||
					stat.equals(MapleBuffStat.MONSTER_RIDING) ||
					stat.equals(MapleBuffStat.ENERGY_CHARGE)) {
				return true;
			}
		}
		return false;
	}

	public static MaplePacket cancelDebuff(List<MapleDisease> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
		long mask = getLongMaskFromListD(statups);

		mplew.writeLong(0);
		mplew.writeLong(mask);

		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 6, 8 });
		mplew.write(owner ? 0 : 1);
		mplew.writeMapleAsciiString(c.getName() + " : " + chat);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopNewVisitor(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 4,2});
		addCharLook(mplew, c, false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}

	public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 4,1});

		addCharLook(mplew, c, false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}

	public static MaplePacket getTradeInvite(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 2,3});
		mplew.writeMapleAsciiString(c.getName());
		mplew.write(new byte[] { (byte) 0xB7, 0x50, 0,0});
		return mplew.getPacket();
	}

	public static MaplePacket getTradeMesoSet(byte number, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xF);
		mplew.write(number);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeItemAdd(byte number, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xE);
		mplew.write(number);
		addItemInfo(mplew, item);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopItemUpdate(MaplePlayerShop shop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0x16);
		mplew.write(shop.getItems().size());
		for (MaplePlayerShopItem item : shop.getItems()) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true, false);
		}
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param shop
	 * @param owner
	 * @return
	 */
	public static MaplePacket getPlayerShop(MapleClient c, MaplePlayerShop shop, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 5, 4,4});
		mplew.write(owner ? 0 : 1);
		mplew.write(0);
		addCharLook(mplew, shop.getOwner(), false);
		mplew.writeMapleAsciiString(shop.getOwner().getName());

		MapleCharacter[] visitors = shop.getVisitors();
		for (int i = 0; i < visitors.length; i++) {
			if (visitors[i] != null) {
				mplew.write(i + 1);
				addCharLook(mplew, visitors[i], false);
				mplew.writeMapleAsciiString(visitors[i].getName());
			}
		}
		mplew.write((byte) 0xFF);
		mplew.writeMapleAsciiString(shop.getDescription());
		List<MaplePlayerShopItem> items = shop.getItems();
		mplew.write(0x10);
		mplew.write(items.size());
		for (MaplePlayerShopItem item : items) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true, false);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getTradeStart(MapleClient c, MapleTrade trade, byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(new byte[] { 5, 3,2});
		mplew.write(number);
		if (number == 1) {
			mplew.write(0);
			addCharLook(mplew, trade.getPartner().getChr(), false);
			mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
		}
		mplew.write(number);
		/*if (number == 1) {
		mplew.write(0);
		mplew.writeInt(c.getPlayer().getId());
		}*/
		addCharLook(mplew, c.getPlayer(), false);
		mplew.writeMapleAsciiString(c.getPlayer().getName());
		mplew.write((byte) 0xFF);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeConfirmation() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0x10);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeCompletion(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(6);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeCancel(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(2);
		return mplew.getPacket();
	}

	public static MaplePacket updateCharBox(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
		mplew.writeInt(c.getId());
		if (c.getPlayerShop() != null) {
			addAnnounceBox(mplew, c.getPlayerShop());
		} else {
			mplew.write(0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalk(int npc, byte msgType, String talk, String endBytes) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(msgType);
		mplew.writeMapleAsciiString(talk);
		mplew.write(HexTool.getByteArrayFromHexString(endBytes));
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkStyle(int npc, String talk, int styles[]) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(7);
		mplew.writeMapleAsciiString(talk);
		mplew.write(styles.length);
		for (int i = 0; i < styles.length; i++) {
			mplew.writeInt(styles[i]);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkNum(int npc, String talk, int def, int min, int max) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(3);
		mplew.writeMapleAsciiString(talk);
		mplew.writeInt(def);
		mplew.writeInt(min);
		mplew.writeInt(max);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkText(int npc, String talk) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(2);
		mplew.writeMapleAsciiString(talk);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket showLevelup(int cid) {
		return showForeignEffect(cid, 0);
	}

	public static MaplePacket showJobChange(int cid) {
		return showForeignEffect(cid, 8);
	}

	public static MaplePacket showForeignEffect(int cid, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid); // ?

		mplew.write(effect);
		return mplew.getPacket();
	}

	public static MaplePacket showBuffeffect(int cid, int skillid, int effectid) {
		return showBuffeffect(cid, skillid, effectid, (byte) 3, false);
	}

	public static MaplePacket showBuffeffect(int cid, int skillid, int effectid, byte direction, boolean morph) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid);
		if (morph) {
			mplew.write(1);
			mplew.writeInt(skillid);
			mplew.write(direction);
		}
		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1); // probably buff level but we don't know it and it
		// doesn't really matter

		if (direction != (byte) 3) {
			mplew.write(direction);
		}

		return mplew.getPacket();
	}

	public static MaplePacket showOwnBuffEffect(int skillid, int effectid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1); // probably buff level but we don't know it and it
		// doesn't really matter

		return mplew.getPacket();
	}

	public static MaplePacket updateSkill(int skillid, int level, int masterlevel) {
		// 0x1E, 0, 1, 1, 0, (byte) 0xE9, 3, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
		mplew.write(1);
		mplew.writeShort(1);
		mplew.writeInt(skillid);
		mplew.writeInt(level);
		mplew.writeInt(masterlevel);
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket updateQuestMobKills(MapleQuestStatus status) {
		// 0x21, 0, 1, (byte) 0xFB, 3, 1, 3, 0, 0x30, 0x30, 31
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(status.getQuest().getId());
		mplew.write(1);
		String killStr = "";
		for (int kills : status.getMobKills().values()) {
			killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3);
		}
		mplew.writeMapleAsciiString(killStr);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowQuestCompletion(int id) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
		mplew.writeShort(id);
		return mplew.getPacket();
	}

	public static MaplePacket getKeymap(Map<Integer, MapleKeyBinding> keybindings) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.KEYMAP.getValue());
		mplew.write(0);

		for (int x = 0; x < 90; x++) {
			MapleKeyBinding binding = keybindings.get(Integer.valueOf(x));
			if (binding != null) {
				mplew.write(binding.getType());
				mplew.writeInt(binding.getAction());
			} else {
				mplew.write(0);
				mplew.writeInt(0);
			}
		}

		return mplew.getPacket();
	}

	public static MaplePacket getWhisper(String sender, int channel, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x12);
		mplew.writeMapleAsciiString(sender);
		mplew.writeShort(channel - 1); // I guess this is the channel

		mplew.writeMapleAsciiString(text);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param target name of the target character
	 * @param reply error code: 0x0 = cannot find char, 0x1 = success
	 * @return the MaplePacket
	 */
	public static MaplePacket getWhisperReply(String target, byte reply) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x0A); // whisper?

		mplew.writeMapleAsciiString(target);
		mplew.write(reply);
		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	public static MaplePacket getFindReplyWithMap(String target, int mapid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(1);
		mplew.writeInt(mapid);
		// ?? official doesn't send zeros here but whatever
		mplew.write(new byte[8]);
		return mplew.getPacket();
	}

	public static MaplePacket getFindReply(String target, int channel) {
		// Received UNKNOWN (1205941596.79689): (25)
		// 0x54, 0, 9, 7, 0, 0x64, 0x61, 0x76, 0x74, 0x73, 0x61, 0x69, 1, (byte) 0x86, 0x7F, 0x3D, 0x36, (byte) 0xD5, 2, 0, 0, 0x22, 00
		// 0, 00
		// T....davtsai..=6...."...
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(3);
		mplew.writeInt(channel - 1);
		return mplew.getPacket();
	}

	public static MaplePacket getInventoryFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowInventoryFull() {
		return getShowInventoryStatus(0xff);
	}

	public static MaplePacket showItemUnavailable() {
		return getShowInventoryStatus(0xfe);
	}

	public static MaplePacket getShowInventoryStatus(int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(0);
		mplew.write(mode);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getStorage(int npcId, byte slots, Collection<IItem> items, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x16);
		mplew.writeInt(npcId);
		mplew.write(slots);
		mplew.writeShort(0x7E);
		mplew.writeShort(0);
		mplew.writeInt(0);
		mplew.writeInt(meso);
		mplew.writeShort(0);
		mplew.write((byte) items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true, false);
		}
		mplew.writeShort(0);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getStorageFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x11);
		return mplew.getPacket();
	}

	public static MaplePacket mesoStorage(byte slots, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x13);
		mplew.write(slots);
		mplew.writeShort(2);
		mplew.writeShort(0);
		mplew.writeInt(0);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}

	public static MaplePacket storeStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0xD);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.writeShort(0);
		mplew.writeInt(0);
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true, false);
			// mplew.write(0);
		}

		return mplew.getPacket();
	}

	public static MaplePacket takeOutStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x9);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.writeShort(0);
		mplew.writeInt(0);
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true, false);
			// mplew.write(0);
		}

		return mplew.getPacket();
	}

	/**
	 * 
	 * @param oid
	 * @param remhp in %
	 * @return
	 */
	public static MaplePacket showMonsterHP(int oid, int remhppercentage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
		mplew.writeInt(oid);
		mplew.write(remhppercentage);

		return mplew.getPacket();
	}

	public static MaplePacket showBossHP(int oid, int currHP, int maxHP, byte tagColor, byte tagBgColor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		//0x53, 0, 5, 0x21, (byte) 0xB3, (byte) 0x81, 0, 0x46, (byte) 0xF2, 0x5E, 1, (byte) 0xC0, (byte) 0xF3, 0x5E, 1, 4, 01
		//0, (byte) 0x81, (byte) 0xB3, 0x21, = 850001, = Pap monster ID
		//1, 0x5E, (byte) 0xF3, (byte) 0xC0, = 23,000,00, = Pap max HP
		//04, 1, - boss bar color/background color as provided in WZ

		mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
		mplew.write(5);
		mplew.writeInt(oid);
		mplew.writeInt(currHP);
		mplew.writeInt(maxHP);
		mplew.write(tagColor);
		mplew.write(tagBgColor);

		return mplew.getPacket();
	}

	public static MaplePacket giveFameResponse(int mode, String charname, int newfame) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(0);
		mplew.writeMapleAsciiString(charname);
		mplew.write(mode);
		mplew.writeShort(newfame);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	/**
	 * status can be: <br>
	 * 0: ok, use giveFameResponse<br>
	 * 1: the username is incorrectly entered<br>
	 * 2: users under level 0x15, are unable to toggle with fame.<br>
	 * 3: can't raise or drop fame anymore today.<br>
	 * 4: can't raise or drop fame for this character for this month anymore.<br>
	 * 5: received fame, use receiveFame()<br>
	 * 6: level of fame neither has been raised nor dropped due to an unexpected
	 * error
	 * 
	 * @param status
	 * @param mode
	 * @param charname
	 * @param newfame
	 * @return
	 */
	public static MaplePacket giveFameErrorResponse(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(status);

		return mplew.getPacket();
	}

	public static MaplePacket receiveFame(int mode, String charnameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
		mplew.write(5);
		mplew.writeMapleAsciiString(charnameFrom);
		mplew.write(mode);

		return mplew.getPacket();
	}

	public static MaplePacket partyCreated() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(8);
		mplew.writeShort(0x8b);
		mplew.writeShort(2);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket partyInvite(MapleCharacter from) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(4);
		mplew.writeInt(from.getParty().getId());
		mplew.writeMapleAsciiString(from.getName());
		mplew.write(0);

		return mplew.getPacket();
	}

	/**
	 * 10: a beginner can't create a party<br>
	 * 11/14/19: your request for a party didn't work due to an unexpected error<br>
	 * 13: you have yet to join a party<br>
	 * 16: already have joined a party<br>
	 * 17: the party you are trying to join is already at full capacity<br>
	 * 18: unable to find the requested character in this channel<br>
	 * 
	 * @param message
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message) {
		// 0x32, 0, 8, (byte) 0xDA, 0x14, 0, 0, (byte) 0xFF, (byte) 0xC9, (byte) 0x9A, 0x3B, (byte) 0xFF, (byte) 0xC9, (byte) 0x9A, 0x3B, 0x22, 3, 0x6E, 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);

		return mplew.getPacket();
	}

	/**
	 * 22: has denied the invitation<br>
	 * 
	 * @param message
	 * @param charname
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message, String charname) {
		// 0x32, 0, 8, (byte) 0xDA, 0x14, 0, 0, (byte) 0xFF, (byte) 0xC9, (byte) 0x9A, 0x3B, (byte) 0xFF, (byte) 0xC9, (byte) 0x9A, 0x3B, 0x22, 3, 0x6E, 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);
		mplew.writeMapleAsciiString(charname);

		return mplew.getPacket();
	}

	private static void addPartyStatus(int forchannel, MapleParty party, LittleEndianWriter lew, boolean leaving) {
		List<MaplePartyCharacter> partymembers = new ArrayList<MaplePartyCharacter>(party.getMembers());
		while (partymembers.size() < 6) {
			partymembers.add(new MaplePartyCharacter());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeAsciiString(StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13));
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getJobId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getLevel());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.isOnline()) {
				lew.writeInt(partychar.getChannel() - 1);
			} else {
				lew.writeInt(-2);
			}
		}
		lew.writeInt(party.getLeader().getId());
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel) {
				lew.writeInt(partychar.getMapid());
			} else {
				lew.writeInt(0);
			}
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel && !leaving) {
				lew.writeInt(partychar.getDoorTown());
				lew.writeInt(partychar.getDoorTarget());
				lew.writeInt(partychar.getDoorPosition().x);
				lew.writeInt(partychar.getDoorPosition().y);
			} else {
				lew.writeInt(0);
				lew.writeInt(0);
				lew.writeInt(0);
				lew.writeInt(0);
			}
		}
	}

	public static MaplePacket updateParty(int forChannel, MapleParty party, PartyOperation op, MaplePartyCharacter target) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		switch (op) {
			case DISBAND:
			case EXPEL:
			case LEAVE:
				mplew.write(0xC);
				mplew.writeInt(40546);
				mplew.writeInt(target.getId());

				if (op == PartyOperation.DISBAND) {
					mplew.write(0);
					mplew.writeInt(party.getId());
				} else {
					mplew.write(1);
					if (op == PartyOperation.EXPEL) {
						mplew.write(1);
					} else {
						mplew.write(0);
					}
					mplew.writeMapleAsciiString(target.getName());
					addPartyStatus(forChannel, party, mplew, false);
					// addLeavePartyTail(mplew);
				}

				break;
			case JOIN:
				mplew.write(0xF);
				mplew.writeInt(40546);
				mplew.writeMapleAsciiString(target.getName());
				addPartyStatus(forChannel, party, mplew, false);
				// addJoinPartyTail(mplew);
				break;
			case SILENT_UPDATE:
			case LOG_ONOFF:
				mplew.write(0x7);
				mplew.writeInt(party.getId());
				addPartyStatus(forChannel, party, mplew, false);
				break;

		}
		return mplew.getPacket();
	}

	public static MaplePacket partyPortal(int townId, int targetId, Point position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.writeShort(0x23);
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		mplew.writeShort(position.x);
		mplew.writeShort(position.y);
		return mplew.getPacket();
	}

	public static MaplePacket updatePartyMemberHP(int cid, int curhp, int maxhp) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(curhp);
		mplew.writeInt(maxhp);
		return mplew.getPacket();
	}

	/**
	 * mode: 0 buddychat; 1 partychat; 2 guildchat
	 * 
	 * @param name
	 * @param chattext
	 * @param mode
	 * @return
	 */
	public static MaplePacket multiChat(String name, String chattext, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MULTICHAT.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(name);
		mplew.writeMapleAsciiString(chattext);
		return mplew.getPacket();
	}

	public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill,
			boolean monsterSkill, int delay) {
		return applyMonsterStatus(oid, stats, skill, monsterSkill, delay, null);
	}

	public static MaplePacket applyMonsterStatusTest(int oid, int mask, int delay, MobSkill mobskill, int value) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// (byte) 0x9B, 0, 0x67, 0x40, 0x6F, 0, (byte) 0x80, 0, 0, 0, 1, 0, (byte) 0xFD, (byte) 0xFE, 0x30, 0, 8, 0, 0x64, 0, 01
		// 0x1D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10, 0, 1, 0, 0x79, 0, 1, 0, (byte) 0xB4, 0x78, 0, 0, 0, 0, (byte) 0x84, 03
		// (byte) 0xB4, 0, (byte) 0xA8, (byte) 0x90, 3, 0, 0, 0, 4, 0, 1, 0, (byte) 0x8C, 0, 3, 0, 0x14, 0, 0x4C, 4, 02
		mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
		mplew.writeInt(oid);

		mplew.writeInt(mask);
		mplew.writeShort(1);
		mplew.writeShort(mobskill.getSkillId());
		mplew.writeShort(mobskill.getSkillLevel());
		mplew.writeShort(0); // as this looks similar to giveBuff this
		// might actually be the buffTime but it's
		// not displayed anywhere

		mplew.writeShort(delay); // delay in ms

		mplew.write(1); // ?

		return mplew.getPacket();
	}

	public static MaplePacket applyMonsterStatusTest2(int oid, int mask, int skill, int value) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// (byte) 0x9B, 0, 0x67, 0x40, 0x6F, 0, (byte) 0x80, 0, 0, 0, 1, 0, (byte) 0xFD, (byte) 0xFE, 0x30, 0, 8, 0, 0x64, 0, 01
		// 0x1D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10, 0, 1, 0, 0x79, 0, 1, 0, (byte) 0xB4, 0x78, 0, 0, 0, 0, (byte) 0x84, 03
		// (byte) 0xB4, 0, (byte) 0xA8, (byte) 0x90, 3, 0, 0, 0, 4, 0, 1, 0, (byte) 0x8C, 0, 3, 0, 0x14, 0, 0x4C, 4, 02
		mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
		mplew.writeInt(oid);

		mplew.writeInt(mask);

		mplew.writeShort(value);
		mplew.writeInt(skill);
		mplew.writeShort(0); // as this looks similar to giveBuff this
		// might actually be the buffTime but it's
		// not displayed anywhere

		mplew.writeShort(0); // delay in ms

		mplew.write(1); // ?

		return mplew.getPacket();
	}

	public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill,
			boolean monsterSkill, int delay, MobSkill mobskill) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// (byte) 0x9B, 0, 0x67, 0x40, 0x6F, 0, (byte) 0x80, 0, 0, 0, 1, 0, (byte) 0xFD, (byte) 0xFE, 0x30, 0, 8, 0, 0x64, 0, 01
		// 0x1D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10, 0, 1, 0, 0x79, 0, 1, 0, (byte) 0xB4, 0x78, 0, 0, 0, 0, (byte) 0x84, 03
		// (byte) 0xB4, 0, (byte) 0xA8, (byte) 0x90, 3, 0, 0, 0, 4, 0, 1, 0, (byte) 0x8C, 0, 3, 0, 0x14, 0, 0x4C, 4, 02
		mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
		mplew.writeInt(oid);

		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);

		for (Integer val : stats.values()) {
			mplew.writeShort(val);
			if (monsterSkill) {
				mplew.writeShort(mobskill.getSkillId());
				mplew.writeShort(mobskill.getSkillLevel());
			} else {
				mplew.writeInt(skill);
			}
			mplew.writeShort(0); // as this looks similar to giveBuff this
			// might actually be the buffTime but it's
			// not displayed anywhere

		}

		mplew.writeShort(delay); // delay in ms

		mplew.write(1); // ?

		return mplew.getPacket();
	}

	public static MaplePacket cancelMonsterStatus(int oid, Map<MonsterStatus, Integer> stats) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_MONSTER_STATUS.getValue());

		mplew.writeInt(oid);
		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);
		mplew.write(1);

		return mplew.getPacket();
	}

	public static MaplePacket getClock(int time) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
		mplew.write(2);
		mplew.writeInt(time);

		return mplew.getPacket();
	}

	public static MaplePacket getClockTime(int hour, int min, int sec) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
		mplew.write(1);
		mplew.write(hour);
		mplew.write(min);
		mplew.write(sec);

		return mplew.getPacket();
	}

	public static MaplePacket spawnMist(int oid, int ownerCid, int skill, int level, MapleMist mist) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
		mplew.writeInt(oid);
		mplew.writeInt(mist.isMobMist() ? 0 : mist.isPoisonMist() ? 1 : 2);
		mplew.writeInt(ownerCid);
		mplew.writeInt(skill);
		mplew.write(level);
		mplew.writeShort(mist.getSkillDelay());
		mplew.writeInt(mist.getBox().x);
		mplew.writeInt(mist.getBox().y);
		mplew.writeInt(mist.getBox().x + mist.getBox().width);
		mplew.writeInt(mist.getBox().y + mist.getBox().height);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket removeMist(int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.REMOVE_MIST.getValue());
		mplew.writeInt(oid);

		return mplew.getPacket();
	}

	public static MaplePacket damageSummon(int cid, int summonSkillId, int damage, int unkByte, int monsterIdFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(unkByte);
		mplew.writeInt(damage);
		mplew.writeInt(monsterIdFrom);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket damageMonster(int oid, int damage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
		mplew.writeInt(oid);
		mplew.write(0);
		mplew.writeInt(damage);

		return mplew.getPacket();
	}

	public static MaplePacket healMonster(int oid, int heal) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
		mplew.writeInt(oid);
		mplew.write(0);
		mplew.writeInt(-heal);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddylist(Collection<BuddylistEntry> buddylist) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(7);
		mplew.write(buddylist.size());
		for (BuddylistEntry buddy : buddylist) {
			if (buddy.isVisible()) {
				mplew.writeInt(buddy.getCharacterId());
				mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13));
				mplew.write(0);
				mplew.writeInt(buddy.getChannel() - 1);
				mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getGroup(), '\0', 17));
			}
		}
		for (int x = 0; x < buddylist.size(); x++) {
			mplew.writeInt(0);
		}
		return mplew.getPacket();

	}

	public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(9);
		mplew.writeInt(cidFrom);
		mplew.writeMapleAsciiString(nameFrom);
		mplew.writeInt(cidFrom);
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
		mplew.write(1);
		mplew.write(31);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeAsciiString(StringUtil.getRightPaddedStr("Default Group", '\0', 17));
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddyChannel(int characterid, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		// 0x2B, 0, 0x14, 0x30, (byte) 0xC0, 0x23, 0, 0, 0x11, 0, 0, 00
		mplew.write(0x14);
		mplew.writeInt(characterid);
		mplew.write(0);
		mplew.writeInt(channel);

		// 0x2B, 0, 0x14, 0x30, (byte) 0xC0, 0x23, 0, 0, 0x0D, 0, 0, 00
		// 0x2B, 0, 0x14, 0x30, 0x75, 0, 0, 0, 0x11, 0, 0, 00
		return mplew.getPacket();
	}

	public static MaplePacket itemEffect(int characterid, int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_EFFECT.getValue());

		mplew.writeInt(characterid);
		mplew.writeInt(itemid);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddyCapacity(int capacity) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(0x15);
		mplew.write(capacity);

		return mplew.getPacket();
	}

	public static MaplePacket showChair(int characterid, int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_CHAIR.getValue());

		mplew.writeInt(characterid);
		mplew.writeInt(itemid);

		return mplew.getPacket();
	}

	public static MaplePacket cancelChair() {
		return cancelChair(-1);
	}

	public static MaplePacket cancelChair(int id) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_CHAIR.getValue());

		if (id == -1) {
			mplew.write(0);
		} else {
			mplew.write(1);
			mplew.writeShort(id);
		}

		return mplew.getPacket();
	}

	// is there a way to spawn reactors non-animated?
	public static MaplePacket spawnReactor(MapleReactor reactor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_SPAWN.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.writeInt(reactor.getId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket triggerReactor(MapleReactor reactor, int stance) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_HIT.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		mplew.writeShort(stance);
		mplew.write(0);

		//frame delay, set to 5 since there doesn't appear to be a fixed formula for it
		mplew.write(5);

		return mplew.getPacket();
	}

	public static MaplePacket destroyReactor(MapleReactor reactor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_DESTROY.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);

		return mplew.getPacket();
	}

	public static MaplePacket musicChange(String song) {
		return environmentChange(song, 6);
	}

	public static MaplePacket showEffect(String effect) {
		return environmentChange(effect, 3);
	}

	public static MaplePacket playSound(String sound) {
		return environmentChange(sound, 4);
	}

	public static MaplePacket environmentChange(String env, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(env);

		return mplew.getPacket();
	}

	public static MaplePacket startMapEffect(String msg, int itemid, boolean active) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
		mplew.write(active ? 0 : 1);

		mplew.writeInt(itemid);
		if (active) {
			mplew.writeMapleAsciiString(msg);
		}
		return mplew.getPacket();
	}

	public static MaplePacket removeMapEffect() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
		mplew.write(0);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket showGuildInfo(MapleCharacter c) {
		//whatever functions calling this better make sure
		//that the character actually HAS a guild
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x1A); //signature for showing guild info

		if (c == null) //show empty guild (used for leaving, expelled)
		{
			mplew.write(0);
			return mplew.getPacket();
		}

		MapleGuildCharacter initiator = c.getMGC();

		MapleGuild g = c.getClient().getChannelServer().getGuild(initiator);

		if (g == null) //failed to read from (byte) 0xDB, - don't show a guild
		{
			mplew.write(0);
			log.warn(MapleClient.getLogMessage(c, "Couldn't load a guild"));
			return mplew.getPacket();
		} else {
			//MapleGuild holds the absolute correct value of guild rank
			//after it is initiated
			MapleGuildCharacter mgc = g.getMGC(c.getId());
			c.setGuildRank(mgc.getGuildRank());
		}

		mplew.write(1); //bInGuild

		mplew.writeInt(c.getGuildId()); //not entirely sure about this one

		mplew.writeMapleAsciiString(g.getName());

		for (int i = 1; i <= 5; i++) {
			mplew.writeMapleAsciiString(g.getRankTitle(i));
		}
		Collection<MapleGuildCharacter> members = g.getMembers();

		mplew.write(members.size());
		//then it is the size of all the members

		for (MapleGuildCharacter mgc : members) //and each of their character ids o_O
		{
			mplew.writeInt(mgc.getId());
		}
		for (MapleGuildCharacter mgc : members) {
			mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
			mplew.writeInt(mgc.getJobId());
			mplew.writeInt(mgc.getLevel());
			mplew.writeInt(mgc.getGuildRank());
			mplew.writeInt(mgc.isOnline() ? 1 : 0);
			mplew.writeInt(g.getSignature());
			mplew.writeInt(3);
		}

		mplew.writeInt(g.getCapacity());
		mplew.writeShort(g.getLogoBG());
		mplew.write(g.getLogoBGColor());
		mplew.writeShort(g.getLogo());
		mplew.write(g.getLogoColor());
		mplew.writeMapleAsciiString(g.getNotice());
		mplew.writeInt(g.getGP());
		mplew.writeInt(0);

		//System.out.println("DEBUG: showGuildInfo packet:\n" + mplew.toString());

		return mplew.getPacket();
	}

	public static MaplePacket guildMemberOnline(int gid, int cid, boolean bOnline) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3d);
		mplew.writeInt(gid);
		mplew.writeInt(cid);
		mplew.write(bOnline ? 1 : 0);

		return mplew.getPacket();
	}

	public static MaplePacket guildInvite(int gid, String charName) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(5);
		mplew.writeInt(gid);
		mplew.writeMapleAsciiString(charName);

		return mplew.getPacket();
	}

	public static MaplePacket genericGuildMessage(byte code) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(code);

		return mplew.getPacket();
	}

	public static MaplePacket newGuildMember(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x27);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
		mplew.writeInt(mgc.getJobId());
		mplew.writeInt(mgc.getLevel());
		mplew.writeInt(mgc.getGuildRank()); //should be always 5 but whatevs

		mplew.writeInt(mgc.isOnline() ? 1 : 0); //should always be 1 too

		mplew.writeInt(1); //? could be guild signature, but doesn't seem to matter

		mplew.writeInt(3);

		return mplew.getPacket();
	}

	//someone leaving, mode == 0x2c for leaving, 0x2f for expelled
	public static MaplePacket memberLeft(MapleGuildCharacter mgc, boolean bExpelled) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(bExpelled ? 0x2f : 0x2c);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeMapleAsciiString(mgc.getName());

		return mplew.getPacket();
	}

	//rank change
	public static MaplePacket changeRank(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x40);
		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.write(mgc.getGuildRank());

		return mplew.getPacket();
	}

	public static MaplePacket guildNotice(int gid, String notice) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x44);

		mplew.writeInt(gid);
		mplew.writeMapleAsciiString(notice);

		return mplew.getPacket();
	}

	public static MaplePacket guildMemberLevelJobUpdate(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3C);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeInt(mgc.getLevel());
		mplew.writeInt(mgc.getJobId());

		return mplew.getPacket();
	}

	public static MaplePacket rankTitleChange(int gid, String[] ranks) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3e);
		mplew.writeInt(gid);

		for (int i = 0; i < 5; i++) {
			mplew.writeMapleAsciiString(ranks[i]);
		}
		return mplew.getPacket();
	}

	public static MaplePacket guildDisband(int gid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x32);
		mplew.writeInt(gid);
		mplew.write(1);

		return mplew.getPacket();
	}

	public static MaplePacket guildEmblemChange(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x42);
		mplew.writeInt(gid);
		mplew.writeShort(bg);
		mplew.write(bgcolor);
		mplew.writeShort(logo);
		mplew.write(logocolor);

		return mplew.getPacket();
	}

	public static MaplePacket guildCapacityChange(int gid, int capacity) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3a);
		mplew.writeInt(gid);
		mplew.write(capacity);

		return mplew.getPacket();
	}

	public static void addThread(MaplePacketLittleEndianWriter mplew, ResultSet rs) throws SQLException {
		mplew.writeInt(rs.getInt("localthreadid"));
		mplew.writeInt(rs.getInt("postercid"));
		mplew.writeMapleAsciiString(rs.getString("name"));
		mplew.writeLong(MaplePacketCreator.getKoreanTimestamp(rs.getLong("timestamp")));
		mplew.writeInt(rs.getInt("icon"));
		mplew.writeInt(rs.getInt("replycount"));
	}

	public static MaplePacket BBSThreadList(ResultSet rs, int start) throws SQLException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
		mplew.write(6);

		if (!rs.last()) //no result at all
		{
			mplew.write(0);
			mplew.writeInt(0);
			mplew.writeInt(0);
			return mplew.getPacket();
		}

		int threadCount = rs.getRow();
		if (rs.getInt("localthreadid") == 0) //has a notice
		{
			mplew.write(1);
			addThread(mplew, rs);
			threadCount--; //one thread didn't count (because it's a notice)

		} else {
			mplew.write(0);
		}
		if (!rs.absolute(start + 1)) //seek to the thread before where we start
		{
			rs.first(); //uh, we're trying to start at a place past possible

			start = 0;
			// System.out.println("Attempting to start past threadCount");
		}

		mplew.writeInt(threadCount);
		mplew.writeInt(Math.min(10, threadCount - start));

		for (int i = 0; i < Math.min(10, threadCount - start); i++) {
			addThread(mplew, rs);
			rs.next();
		}

		return mplew.getPacket();
	}

	public static MaplePacket showThread(int localthreadid, ResultSet threadRS, ResultSet repliesRS)
			throws SQLException,
			RuntimeException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
		mplew.write(7);

		mplew.writeInt(localthreadid);
		mplew.writeInt(threadRS.getInt("postercid"));
		mplew.writeLong(getKoreanTimestamp(threadRS.getLong("timestamp")));
		mplew.writeMapleAsciiString(threadRS.getString("name"));
		mplew.writeMapleAsciiString(threadRS.getString("startpost"));
		mplew.writeInt(threadRS.getInt("icon"));

		if (repliesRS != null) {
			int replyCount = threadRS.getInt("replycount");
			mplew.writeInt(replyCount);

			int i;
			for (i = 0; i < replyCount && repliesRS.next(); i++) {
				mplew.writeInt(repliesRS.getInt("replyid"));
				mplew.writeInt(repliesRS.getInt("postercid"));
				mplew.writeLong(getKoreanTimestamp(repliesRS.getLong("timestamp")));
				mplew.writeMapleAsciiString(repliesRS.getString("content"));
			}

			if (i != replyCount || repliesRS.next()) {
				//in the unlikely event that we lost count of replyid
				throw new RuntimeException(String.valueOf(threadRS.getInt("threadid")));
				//we need to fix the database and stop the packet sending
				//or else it'll probably error 0x38, whoever tries to read it

				//there is ONE case not checked, and that's when the thread
				//has a replycount of 0 and there is one or more replies to the
				//thread in bbs_replies
			}
		} else {
			mplew.writeInt(0); //0 replies

		}
		return mplew.getPacket();
	}

	public static MaplePacket showGuildRanks(int npcid, ResultSet rs) throws SQLException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x49);
		mplew.writeInt(npcid);
		if (!rs.last()) //no guilds o.o
		{
			mplew.writeInt(0);
			return mplew.getPacket();
		}

		mplew.writeInt(rs.getRow());		//number of entries

		rs.beforeFirst();
		while (rs.next()) {
			mplew.writeMapleAsciiString(rs.getString("name"));
			mplew.writeInt(rs.getInt("GP"));
			mplew.writeInt(rs.getInt("logo"));
			mplew.writeInt(rs.getInt("logoColor"));
			mplew.writeInt(rs.getInt("logoBG"));
			mplew.writeInt(rs.getInt("logoBGColor"));
		}

		return mplew.getPacket();
	}

	public static MaplePacket updateGP(int gid, int GP) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x48);
		mplew.writeInt(gid);
		mplew.writeInt(GP);

		return mplew.getPacket();
	}

	public static MaplePacket skillEffect(MapleCharacter from, int skillId, byte flags) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SKILL_EFFECT.getValue());
		mplew.writeInt(from.getId());
		mplew.writeInt(skillId);
		mplew.write(1); // unknown at this point

		mplew.write(flags);
		mplew.write(4); // unknown at this point

		return mplew.getPacket();
	}

	public static MaplePacket skillCancel(MapleCharacter from, int skillId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_SKILL_EFFECT.getValue());
		mplew.writeInt(from.getId());
		mplew.writeInt(skillId);
		return mplew.getPacket();
	}

	public static MaplePacket showMagnet(int mobid, byte success) {  // Monster Magnet

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_MAGNET.getValue());
		mplew.writeInt(mobid);
		mplew.write(success);
		return mplew.getPacket();
	}

	/**
	 * Sends a Player Hint, something that pops up above your character!
	 * 
	 * @return The packet.
	 */
	public static MaplePacket sendHint(String hint) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.PLAYER_HINT.getValue());
		mplew.writeMapleAsciiString(hint);
		mplew.write(new byte[] { (byte) 0xFA, 0, 5, 0,1});

		return mplew.getPacket();
	}

	public static MaplePacket messengerInvite(String from, int messengerid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(3);
		mplew.writeMapleAsciiString(from);
		mplew.write(0);
		mplew.writeInt(messengerid);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket addMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(0);
		mplew.write(position);
		addCharLook(mplew, chr, true);
		mplew.writeMapleAsciiString(from);
		mplew.write(channel);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket removeMessengerPlayer(int position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(2);
		mplew.write(position);
		return mplew.getPacket();
	}

	public static MaplePacket updateMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(7);
		mplew.write(position);
		addCharLook(mplew, chr, true);
		mplew.writeMapleAsciiString(from);
		mplew.write(channel);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket joinMessenger(int position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(1);
		mplew.write(position);
		return mplew.getPacket();
	}

	public static MaplePacket messengerChat(String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(6);
		mplew.writeMapleAsciiString(text);
		return mplew.getPacket();
	}

	public static MaplePacket messengerNote(String text, int mode, int mode2) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(text);
		mplew.write(mode2);
		return mplew.getPacket();
	}

	public static MaplePacket warpCS(MapleClient c) {

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		MapleCharacter chr = c.getPlayer();

		mplew.writeShort(SendPacketOpcode.CS_OPEN.getValue());
		mplew.writeLong(-1);

		addCharStats(mplew, chr);

		mplew.write(20); // ???

		mplew.writeInt(chr.getMeso()); // mesos

		mplew.write(100); // equip slots

		mplew.write(100); // use slots

		mplew.write(100); // set-up slots

		mplew.write(100); // etc slots

		mplew.write(100); // storage slots

		MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
		Collection<IItem> equippedC = iv.list();
		List<Item> equipped = new ArrayList<Item>(equippedC.size());
		for (IItem item : equippedC) {
			equipped.add((Item) item);
		}
		Collections.sort(equipped);

		for (Item item : equipped) {
			addItemInfo(mplew, item);
		}
		mplew.writeShort(0); // start of equip inventory

		iv = chr.getInventory(MapleInventoryType.EQUIP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of use inventory

		iv = chr.getInventory(MapleInventoryType.USE);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of set-up inventory

		iv = chr.getInventory(MapleInventoryType.SETUP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of etc inventory

		iv = chr.getInventory(MapleInventoryType.ETC);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of cash inventory

		iv = chr.getInventory(MapleInventoryType.CASH);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}

		mplew.write(0);

		Map<ISkill, MapleCharacter.SkillEntry> skills = chr.getSkills();
		mplew.writeShort(skills.size());
		for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
			mplew.writeInt(skill.getKey().getId());
			mplew.writeInt(skill.getValue().skillevel);
			if (skill.getKey().isFourthJob()) {
				mplew.writeInt(skill.getValue().masterlevel);
			}
		}

		mplew.writeShort(0);

		mplew.writeShort(1);
		mplew.writeInt(662990);
		mplew.write(new byte[] { 0x5A, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0,0});
		//mplew.writeLong(0);
		//mplew.writeLong(0);

		mplew.writeLong(0);

		for (int i = 0; i < 15; i++) {
			mplew.write(CHAR_INFO_MAGIC);
		}

		mplew.writeInt(0);
		mplew.write(1);

		mplew.writeMapleAsciiString(chr.getClient().getAccountName());
		mplew.writeInt(40);//Event Items

		mplew.write(new byte[] { (byte) 0xF2, 0x20, (byte) 0x9A, 0, (byte) 0xF3, 0x20, (byte) 0x9A, 0, (byte) 0xF4, 0x20, (byte) 0x9A, 0, (byte) 0xF5, 0x20, (byte) 0x9A, 0, (byte) 0xF6, 0x20, (byte) 0x9A, 0, (byte) 0xF7, 0x20, (byte) 0x9A, 0, (byte) 0xF8, 0x20, (byte) 0x9A, 0, (byte) 0xF9, 0x20, (byte) 0x9A, 0, (byte) 0xFA, 0x20, (byte) 0x9A, 0, (byte) 0xFB, 0x20, (byte) 0x9A, 0, (byte) 0xFC, 0x20, (byte) 0x9A, 0, (byte) 0xFD, 0x20, (byte) 0x9A, 0, (byte) 0xFE, 0x20, (byte) 0x9A, 0, (byte) 0xFF, 0x20, (byte) 0x9A, 0, 0, 0x21, (byte) 0x9A, 0, 1, 0x21, (byte) 0x9A, 0, 2, 0x21, (byte) 0x9A, 0, 3, 0x21, (byte) 0x9A, 0, 4, 0x21, (byte) 0x9A, 0, 5, 0x21, (byte) 0x9A, 0, 6, 0x21, (byte) 0x9A, 0, 7, 0x21, (byte) 0x9A, 0, 8, 0x21, (byte) 0x9A, 0, 9, 0x21, (byte) 0x9A, 0, 0x0A, 0x21, (byte) 0x9A, 0, 0x0B, 0x21, (byte) 0x9A, 0, 0x0C, 0x21, (byte) 0x9A, 0, 0x0D, 0x21, (byte) 0x9A, 0, 0x0E, 0x21, (byte) 0x9A, 0, 0x0F, 0x21, (byte) 0x9A, 0, 0x10, 0x21, (byte) 0x9A, 0, 0x11, 0x21, (byte) 0x9A, 0, 0x12, 0x21, (byte) 0x9A, 0, 0x13, 0x21, (byte) 0x9A, 0, 0x14, 0x21, (byte) 0x9A, 0, 0x15, 0x21, (byte) 0x9A, 0, 0x16, 0x21, (byte) 0x9A, 0, 0x17, 0x21, (byte) 0x9A, 0, 0x18, 0x21, (byte) 0x9A, 0, 0x19, 0x21, (byte) 0x9A,0});
		mplew.writeShort((byte) 0xD8);//New Items

		mplew.write(new byte[] { 0x46, (byte) 0x9D, (byte) 0x98, 0, 8, 2, 7, 0, 0x47, (byte) 0x9D, (byte) 0x98, 0, 8, 2, 7, 0, 0x48, (byte) 0x9D, (byte) 0x98, 0, 8, 2, 7, 0, 0x49, (byte) 0x9D, (byte) 0x98, 0, 8, 2, 7, 0, 0x4A, (byte) 0x9D, (byte) 0x98, 0, 8, 2, 7, 0, 0x4B, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x4C, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x4D, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x4E, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x4F, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x50, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x51, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x52, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x53, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x54, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x55, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x56, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x57, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x58, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x59, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5A, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5B, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5C, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5D, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5E, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x5F, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x60, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x61, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x62, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x63, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x64, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x65, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x66, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x67, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x68, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x69, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x6A, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x6B, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x6C, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x6D, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x6E, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x7C, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x7D, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x7E, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, 0x7F, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) (byte) 0x80, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) (byte) 0x81, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) (byte) 0x82, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) (byte) 0x83, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x84, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x85, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x86, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x87, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x88, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x89, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0x8A, (byte) 0x9D, (byte) 0x98, 0, 0, 2, 0, (byte) 0xF2, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF3, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF4, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF5, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF6, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF7, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF8, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xF9, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFA, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFB, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFC, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFD, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFE, 0x20, (byte) 0x9A, 0, 0, 2, 1, (byte) 0xFF, 0x20, (byte) 0x9A, 0, 0, 2, 1, 0, 0x21, (byte) 0x9A, 0, 0, 2, 1, 1, 0x21, (byte) 0x9A, 0, 0, 2, 1, 2, 0x21, (byte) 0x9A, 0, 0, 2, 1, 3, 0x21, (byte) 0x9A, 0, 0, 2, 1, 4, 0x21, (byte) 0x9A, 0, 0, 2, 1, 5, 0x21, (byte) 0x9A, 0, 0, 2, 1, 6, 0x21, (byte) 0x9A, 0, 0, 2, 1, 7, 0x21, (byte) 0x9A, 0, 0, 2, 1, 8, 0x21, (byte) 0x9A, 0, 0, 2, 1, 9, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0A, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0B, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0C, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0D, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0E, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x0F, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x10, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x11, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x12, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x13, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x14, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x15, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x16, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x17, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x18, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x19, 0x21, (byte) 0x9A, 0, 0, 2, 1, 0x1A, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x1B, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x1C, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x1D, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x1E, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x1F, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x20, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x21, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x22, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x23, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x24, 0x21, (byte) 0x9A, 0, 0, 2, 0, 0x15, 0x2D, 0x31, 1, 8, 0, 9, 0x2B, 0x2D, 0x31, 1, 8, 0, 9, 0x4C, 0x2D, 0x31, 1, 8, 0, 9, (byte) 0xEC, 0x2D, 0x31, 1, 8, 0, 9, (byte) 0xD3, 0x2E, 0x31, 1, 8, 0, 7, (byte) 0xD4, 0x2E, 0x31, 1, 8, 0, 7, (byte) 0xD5, 0x2E, 0x31, 1, 8, 0, 7, (byte) 0xD6, 0x2E, 0x31, 1, 8, 4, 8, 2, (byte) 0xD7, 0x2E, 0x31, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xD8, 0x2E, 0x31, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xD9, 0x2E, 0x31, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xDA, 0x2E, 0x31, 1, 0, 2, 0, (byte) 0xDB, 0x2E, 0x31, 1, 0, 2, 0, (byte) 0xDC, 0x2E, 0x31, 1, 0, 2, 0, (byte) 0xA4, (byte) 0xB3, 0x32, 1, 8, 0, 9, (byte) 0xB5, (byte) 0xB3, 0x32, 1, 8, 0, 9, (byte) 0xCE, (byte) 0xB3, 0x32, 1, 8, 0, 9, (byte) 0xD1, (byte) 0xB3, 0x32, 1, 8, 0, 9, (byte) 0xE4, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xE9, (byte) 0xB3, 0x32, 1, 8, 4, 8, 2, (byte) 0xEC, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xED, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xEE, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xEF, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF0, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF1, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF2, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF3, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF4, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF5, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF6, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF7, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF8, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xF9, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFA, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFB, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFC, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFD, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFE, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xFF, (byte) 0xB3, 0x32, 1, 8, 4, 7, (byte) 0xFF, 0, (byte) 0xB4, 0x32, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xE9, (byte) 0xC0, 0x35, 1, 8, 0, 8, (byte) 0x85, (byte) 0xC1, 0x35, 1, 8, 0, 8, (byte) 0xC5, (byte) 0xC1, 0x35, 1, 8, 0, 8, 0x10, (byte) 0xC2, 0x35, 1, 8, 4, 7, (byte) 0xFF, 0x12, (byte) 0xC2, 0x35, 1, 0, 2, 0, 0x13, (byte) 0xC2, 0x35, 1, 0, 2, 0, (byte) 0x9C, 0x47, 0x37, 1, 8, 0, 8, 0x1A, 0x48, 0x37, 1, 8, 0, 8, 0x2B, 0x48, 0x37, 1, 8, 0, 8, 0x5E, 0x48, 0x37, 1, 8, 0, 8, 0x65, 0x48, 0x37, 1, 8, 4, 7, (byte) 0xFF, 0x6A, 0x48, 0x37, 1, 0, 2, 0, 0x3B, (byte) 0xCE, 0x38, 1, 8, 0, 8, 0x67, (byte) 0xCE, 0x38, 1, 8, 0, 8, (byte) 0x9F, (byte) 0xCE, 0x38, 1, 8, 0, 8, (byte) 0xD4, (byte) 0xCE, 0x38, 1, 0, 2, 0, (byte) 0xEE, 0x54, 0x3A, 1, 8, 0, 9, (byte) 0x90, 0x55, 0x3A, 1, 0, 2, 0, (byte) 0xC5, 0x62, 0x3D, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xD7, 0x62, 0x3D, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xD8, 0x62, 0x3D, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xD9, 0x62, 0x3D, 1, 0, 6, 0, (byte) 0xFF, (byte) 0xDA, 0x62, 0x3D, 1, 0, 6, 0, (byte) 0xFF, (byte) 0xDB, 0x62, 0x3D, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xDC, 0x62, 0x3D, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xDF, 0x62, 0x3D, 1, 0, 2, 0, (byte) 0xE0, 0x62, 0x3D, 1, 0, 2, 0, (byte) 0xE1, 0x62, 0x3D, 1, 0, 2, 0, (byte) 0xE2, 0x62, 0x3D, 1, 0, 2, 0, (byte) 0xE3, 0x62, 0x3D, 1, 0, 2, 0, (byte) 0xBC, (byte) 0xE8, 0x3E, 1, 8, 0, 8, (byte) 0xDA, (byte) 0xE8, 0x3E, 1, 8, 0, 8, (byte) 0xDC, (byte) 0xE8, 0x3E, 1, 8, 0, 8, (byte) 0xDD, (byte) 0xE8, 0x3E, 1, 8, 0, 8, (byte) 0xE2, (byte) 0xE8, 0x3E, 1, 8, 4, 8, 2, (byte) 0xE4, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xE6, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xE8, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xEA, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xEC, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xEE, (byte) 0xE8, 0x3E, 1, 0, 2, 0, (byte) 0xE2, (byte) 0xF5, 0x41, 1, 8, 0, 8, (byte) 0xF8, (byte) 0xF5, 0x41, 1, 8, 0, 8, (byte) 0xF9, (byte) 0xF5, 0x41, 1, 8, 0, 8, (byte) 0xFC, (byte) 0xF5, 0x41, 1, 8, 4, 8, 2, 9, (byte) 0xF6, 0x41, 1, 8, 0, 8, 0x0E, (byte) 0xF6, 0x41, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xC5, (byte) 0xD0, (byte) 0xCC, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xC9, (byte) 0xD0, (byte) 0xCC, 1, 8, 4, 8, 2, (byte) 0xCB, (byte) 0xD0, (byte) 0xCC, 1, 8, 0, 8, (byte) 0xCD, (byte) 0xD0, (byte) 0xCC, 1, 8, 4, 7, (byte) 0xFF, (byte) 0xDE, (byte) 0xD0, (byte) 0xCC, 1, 0, 4, 2, (byte) 0xA0, (byte) 0x91, 2, 3, 8, 4, 7, (byte) 0xFF, (byte) 0xA1, (byte) 0x91, 2, 3, 8, 0, 8, (byte) 0xB8, (byte) 0x91, 2, 3, 8, 0, 8, (byte) 0xBA, (byte) 0x91, 2, 3, 8, 4, 7, (byte) 0xFF, (byte) 0xBB, (byte) 0x91, 2, 3, 8, 0, 8, (byte) 0xCD, (byte) 0x91, 2, 3, 8, 4, 8, 2, (byte) 0xCE, (byte) 0x91, 2, 3, 8, 4, 8, 2, (byte) 0xCF, (byte) 0x91, 2, 3, 8, 4, 7, (byte) 0xFF, (byte) 0xD1, (byte) 0x91, 2, 3, 0, 2, 0, (byte) 0xD2, (byte) 0x91, 2, 3, 0, 2, 0, (byte) 0xA0, 0x0D, (byte) 0x95, 3, 8, 0, 9, (byte) 0xA4, 0x0D, (byte) 0x95, 3, 8, 0, 9, (byte) 0xC9, 0x0D, (byte) 0x95, 3, 8, 0, 9, (byte) 0xCA, 0x0D, (byte) 0x95, 3, 8, 0, 9, (byte) 0xD3, 0x0D, (byte) 0x95, 3, 8, 4, 7, (byte) 0xFF, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 8, 4, 8, 2, 0, 0x2E, 0, 0x35, 0, 0x38, 0, 0, 0, 3, 0, 0x0D, 0, 0x65, 1, 0x0E, 5, 4, 0, 0, 0, 0x34, 0, 0x39, 0, 0, 0, 0, 0, 0x31, 0, 0x35, 0, 7, 0, 0x10, 0, 0x66, 0, 0x0C, 5, (byte) 0xB0, 1, 0x14, 0, (byte) 0xF8, (byte) 0xC2, 0x51, 2, 0x67, 0, 0x69, 0, 0x6E, 0, 0x49, 0, 0x70, 0, 0, 0, 0, 0, 0x70, 0, 3, 0, 4, 0, 0x62, 0, 8, 5, (byte) 0x90, 1, 0x14, 0, (byte) 0xD8, 0x27, (byte) 0xF1, 1, 0x64, 0, 0x49, 0, 0x70, 0, 0, 0, 3, 0, 0x17, 0, 0x6F, 1, 0x0E, 5, 4, 0, 0, 0, 0x34, 0, 0x39, 0, 0, 0, 0, 0, 0x6D, 0, 0x61, 0, 5, 0, 0x1A, 0, 0x68, 1, 8, 5, 1, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 1, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 1, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 1, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 1, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 1, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 1, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 1, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 2, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 2, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 2, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 2, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 2, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 2, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 2, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 2, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 3, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 3, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 3, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 3, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 3, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 3, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 3, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 3, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 4, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 4, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 4, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 4, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 4, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 4, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 4, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 4, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 4, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 4, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 5, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 5, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 5, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 5, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 5, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 5, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 5, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 5, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 5, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 5, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 6, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 6, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 6, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 6, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 6, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 6, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 6, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 6, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 6, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 6, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 7, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 7, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 7, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 7, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 7, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 7, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 7, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 7, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 7, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 7, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 8, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 8, 0, 0, 0, 0, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 8, 0, 0, 0, 0, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 8, 0, 0, 0, 0, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 8, 0, 0, 0, 0, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 8, 0, 0, 0, 1, 0, 0, 0, (byte) 0xC3, (byte) 0xFD, (byte) 0xFD, 2, 8, 0, 0, 0, 1, 0, 0, 0, 0x34, 0x77, (byte) 0xFC, 2, 8, 0, 0, 0, 1, 0, 0, 0, 0x39, 0x4A, (byte) 0xCB, 1, 8, 0, 0, 0, 1, 0, 0, 0, (byte) 0xE0, 0x0D, (byte) 0x95, 3, 8, 0, 0, 0, 1, 0, 0, 0, (byte) 0x89, (byte) 0xC3, (byte) 0xC9, 1, 0, 0, 1, 0, 0x49, 0x4B, 0x4C, 0, 2, 0x21, (byte) 0x9A, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0x0F, 0, 0, 0, 0, 0, 0, 0, 0x0F, 0, 0, 0, (byte) 0xBE, 0x68, 0x32, 1, (byte) 0xBE, 0x68, 0x32, 1, 0x0B, 0, 0, 0, 0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,1});
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static void toCashItem(MaplePacketLittleEndianWriter mplew, int sn, int type1, int type2) {
		// (byte) 0xE1, (byte) 0x9C, (byte) 0x98, 0, 0, 6, 0, 0, 0, - Globe Cap
		mplew.writeInt(sn);
		mplew.write(0);
		mplew.write(type1);
		mplew.writeShort(0);
		mplew.write(type2);
	}

	public static void toCashItem(MaplePacketLittleEndianWriter mplew, int sn, int type0, int type1, int type2) {
		mplew.writeInt(sn);
		mplew.write(type0);
		mplew.write(type1);
		mplew.writeShort(0);
		mplew.write(type2);
	}

	public static MaplePacket showNXMapleTokens(MapleCharacter chr) {

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_UPDATE.getValue());
		mplew.writeInt(chr.getCSPoints(0)); // NX

		mplew.writeInt(chr.getCSPoints(1)); // Maple Points

		mplew.writeInt(chr.getCSPoints(2)); // Gift Tokens

		return mplew.getPacket();
	}

	public static MaplePacket showBoughtCSItem(int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// (byte) 0xFE, 00
		// 0x3B, 0x32, (byte) 0xF0, 00
		// 0, 0, 0, 00
		// 00
		// (byte) 0x80, (byte) 0xB0, 0x41, 00
		// 0, 0, 0, 00
		// 5, 0x20, 0x4E, 0, - item ID
		// (byte) 0xCD, (byte) 0xD0, (byte) 0xCC, 1, 1, 0, 0, 4, (byte) 0xF1, 0x4B, 0x40, 0, (byte) 0x80, 0, 0, 0, 1, 0, 0, (byte) 0xE0, (byte) 0xCE, 0x0D, 0x5F, (byte) 0xBB, 0x53, (byte) 0xC9, 1, 0, 0, 0, 0, 0, 0, 0, 00

		// (byte) 0xFE, 00
		// 0x3B, 0x41, 0x4F, 32
		// 0, 0, 0, 00
		// 00
		// (byte) 0x80, (byte) 0xB0, 0x41, 00
		// 0, 0, 0, 00
		// 5, 0x20, 0x4E, 0, - item ID
		// (byte) 0xCD, (byte) 0xD0, (byte) 0xCC, 1, 1, 0, 0, 0x0A, (byte) 0xF1, 0x4B, 0x40, 0, (byte) 0x80, 0, 0, 0, 1, 0, 0, 0x10, (byte) 0xCB, (byte) 0xE9, 0x4A, (byte) 0xBD, 0x53, (byte) 0xC9, 01
		// 0, 0, 0, 0, 0, 0, 0, 00
		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());

		mplew.writeInt(15741499);
		mplew.writeInt(0);
		mplew.writeShort(0);
		mplew.writeInt(4305024);
		mplew.writeInt(0);
		mplew.writeInt(itemid);
		mplew.write(new byte[] { (byte) 0xCD, (byte) 0xD0, (byte) 0xCC, 1, 1, 0, 0, 0x0A, (byte) 0xF1, 0x4B, 0x40, 0, (byte) 0x80, 0, 0, 0, 1, 0, 0, 0x10, (byte) 0xCB, (byte) 0xE9, 0x4A, (byte) 0xBD, 0x53, (byte) 0xC9,1});
		mplew.writeLong(0);

		return mplew.getPacket();
	}

	public static MaplePacket showCouponRedeemedItem(int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.writeShort(0x3A);
		mplew.writeInt(0);
		mplew.writeInt(1);
		mplew.writeShort(1);
		mplew.writeShort(0x1A);
		mplew.writeInt(itemid);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket enableCSUse0() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.write(new byte[] { 0x12, 0, 0, 0, 0, 0,0});

		return mplew.getPacket();
	}

	public static MaplePacket enableCSUse1() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// (byte) 0xFE, 0, 0x2F, 0, 0, 4, 0, 3, 00

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.writeShort(0x2F);
		mplew.write(0);
		mplew.writeShort(4);
		mplew.writeShort(3);

		return mplew.getPacket();
	}

	public static MaplePacket enableCSUse2() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());

		mplew.write(0x31);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket enableCSUse3() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x33);
		mplew.write(new byte[40]);

		return mplew.getPacket();
	}

	// Decoding : Raz (Snow) | Author : Penguins (Acrylic)
	public static MaplePacket sendWishList(int characterid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort((byte) 0xEF);
		mplew.write(0x30);

		Connection con = DatabaseConnection.getConnection();
		int i = 10;

		try {
			PreparedStatement ps = con.prepareStatement("SELECT sn FROM wishlist WHERE charid = ? LIMIT 10");
			ps.setInt(1, characterid);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				mplew.writeInt(rs.getInt("sn"));
			}
			rs.close();
			ps.close();
		} catch (SQLException se) {
			System.out.println("Sql Error with wishlists");
		}

		while (i > 0) {
			mplew.writeInt(0);
			i--;
		}
		return mplew.getPacket();
	}

	// Decoding : Raz (Snow) | Author : Penguins (Acrylic)
	/*public static MaplePacket updateWishList(int characterid) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
	mplew.writeShort((byte) 0xEF);
	mplew.write(0x36);
	
	Connection con = DatabaseConnection.getConnection();
	int i = 10;
	
	try {
	PreparedStatement ps = con.prepareStatement("SELECT sn FROM wishlist WHERE charid = ? LIMIT 10");
	ps.setInt(1, characterid);
	ResultSet rs = ps.executeQuery();
	while (rs.next()) {
	mplew.writeInt(rs.getInt("sn"));
	}
	rs.close();
	ps.close();
	} catch (SQLException se) {
	}
	
	while (i > 0) {
	mplew.writeInt(0);
	i--;
	}
	return mplew.getPacket();
	}*/
	public static MaplePacket wrongCouponCode() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// (byte) 0xFE, 0, 0x40, 87
		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x40);
		mplew.write((byte) 0x87);

		return mplew.getPacket();
	}

	public static MaplePacket getFindReplyWithCS(String target) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(2);
		mplew.writeInt(-1);
		return mplew.getPacket();
	}

	public static MaplePacket updatePet(MaplePet pet, boolean alive) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(0);
		mplew.write(2);
		mplew.write(3);
		mplew.write(5);
		mplew.write(pet.getPosition());
		mplew.writeShort(0);
		mplew.write(5);
		mplew.write(pet.getPosition());
		mplew.write(0);
		mplew.write(3);
		mplew.writeInt(pet.getItemId());
		mplew.write(1);
		mplew.writeInt(pet.getUniqueId());
		mplew.writeInt(0);
		mplew.write(0);
		mplew.write(ITEM_MAGIC);
		addExpirationTime(mplew, 0, false);
		String petname = pet.getName();
		if (petname.length() > 13) {
			petname = petname.substring(0, 13);
		}
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(petname, '\0', 13));
		mplew.write(pet.getLevel());
		mplew.writeShort(pet.getCloseness());
		mplew.write(pet.getFullness());
		if (alive) {
			mplew.writeLong(getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
			mplew.writeInt(0);
		} else {
			mplew.write(0);
			mplew.write(ITEM_MAGIC);
			addExpirationTime(mplew, 0, false);
			mplew.writeInt(0);
		}
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket showPet(MapleCharacter chr, MaplePet pet, boolean remove) {
		return showPet(chr, pet, remove, false);
	}

	public static MaplePacket showPet(MapleCharacter chr, MaplePet pet, boolean remove, boolean hunger) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_PET.getValue());

		mplew.writeInt(chr.getId());
		mplew.write(chr.getPetIndex(pet));
		if (remove) {
			mplew.write(0);
			mplew.write(hunger ? 1 : 0);
		} else {
			mplew.write(1);
			mplew.write(0);
			mplew.writeInt(pet.getItemId());
			mplew.writeMapleAsciiString(pet.getName());
			mplew.writeInt(pet.getUniqueId());
			mplew.writeInt(0);
			mplew.writeShort(pet.getPos().x);
			mplew.writeShort(pet.getPos().y);
			mplew.write(pet.getStance());
			mplew.writeInt(pet.getFh());
		}

		return mplew.getPacket();
	}

	public static MaplePacket movePet(int cid, int pid, int slot, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_PET.getValue());
		mplew.writeInt(cid);
		mplew.write(slot);
		mplew.writeInt(pid);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket petChat(int cid, int index, int act, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PET_CHAT.getValue());
		mplew.writeInt(cid);
		mplew.write(index);
		mplew.write(0);
		mplew.write(act);
		mplew.writeMapleAsciiString(text);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket commandResponse(int cid, int index, int animation, boolean success) {
		// (byte) 0x84, 0, 9, 3, 0x2C, 0, 0, 0, 0x19, 0, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// (byte) 0x84, 0, (byte) 0xE6, (byte) 0xDC, 0x17, 0, 0, 1, 0, 00
		mplew.writeShort(SendPacketOpcode.PET_COMMAND.getValue());
		mplew.writeInt(cid);
		mplew.write(index);
		mplew.write((animation == 1 && success) ? 1 : 0);
		mplew.write(animation);
		if (animation == 1) {
			mplew.write(0);
		} else {
			mplew.writeShort(success ? 1 : 0);
		}

		return mplew.getPacket();
	}

	public static MaplePacket showOwnPetLevelUp(int index) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
		mplew.write(4);
		mplew.write(0);
		mplew.write(index);//Pet Index

		return mplew.getPacket();
	}

	public static MaplePacket showPetLevelUp(MapleCharacter chr, int index) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(chr.getId());
		mplew.write(4);
		mplew.write(0);
		mplew.write(index);

		return mplew.getPacket();
	}

	public static MaplePacket changePetName(MapleCharacter chr, String newname, int slot) {
		// (byte) 0x82, 0, (byte) 0xE6, (byte) 0xDC, 0x17, 0, 0, 4, 0, 0x4A, 0x65, 0x66, 0x66, 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PET_NAMECHANGE.getValue());
		mplew.writeInt(chr.getId());
		mplew.write(slot);
		mplew.writeMapleAsciiString(newname);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket petStatUpdate(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());

		int mask = 0;
		mask |= MapleStat.PET.getValue();
		mplew.write(0);
		mplew.writeInt(mask);
		MaplePet[] pets = chr.getPets();
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				mplew.writeInt(pets[i].getUniqueId());
			} else {
				mplew.writeInt(0);
			}
			mplew.writeInt(0);
		}
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket showForcedEquip() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FORCED_EQUIP.getValue());
		return mplew.getPacket();
	}

	public static MaplePacket skillCooldown(int sid, int time) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.COOLDOWN.getValue());

		mplew.writeInt(sid);
		mplew.writeShort(time);

		return mplew.getPacket();
	}

	public static MaplePacket skillBookSuccess(MapleCharacter chr, int skillid,
			int maxlevel, boolean canuse, boolean success) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.USE_SKILL_BOOK.getValue());
		mplew.writeInt(chr.getId()); // character id

		mplew.write(1);
		mplew.writeInt(skillid);
		mplew.writeInt(maxlevel);
		mplew.write(canuse ? 1 : 0);
		mplew.write(success ? 1 : 0);
		return mplew.getPacket();
	}

	public static MaplePacket getMacros(SkillMacro[] macros) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SKILL_MACRO.getValue());
		int count = 0;
		for (int i = 0; i < 5; i++) {
			if (macros[i] != null) {
				count++;
			}
		}
		mplew.write(count); // number of macros

		for (int i = 0; i < 5; i++) {
			SkillMacro macro = macros[i];
			if (macro != null) {
				mplew.writeMapleAsciiString(macro.getName());
				mplew.write(macro.getShout());
				mplew.writeInt(macro.getSkill1());
				mplew.writeInt(macro.getSkill2());
				mplew.writeInt(macro.getSkill3());
			}
		}

		return mplew.getPacket();
	}

	public static MaplePacket showNotes(ResultSet notes, int count) throws SQLException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_NOTES.getValue());

		mplew.write(2);
		mplew.write(count);

		for (int i = 0; i < count; i++) {
			mplew.writeInt(notes.getInt("id"));
			mplew.writeMapleAsciiString(notes.getString("from"));
			mplew.writeMapleAsciiString(notes.getString("message"));
			mplew.writeLong(getKoreanTimestamp(notes.getLong("timestamp")));
			mplew.write(0);
			notes.next();
		}

		return mplew.getPacket();
	}

	public static void sendUnkwnNote(String to, String msg, String from) throws SQLException {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)");
		ps.setString(1, to);
		ps.setString(2, from);
		ps.setString(3, msg);
		ps.setLong(4, System.currentTimeMillis());
		ps.executeUpdate();
		ps.close();
	}

	/**
	 * Gets a gm effect packet (ie. hide, banned, etc.)
	 *
	 * Possible values for <code>type</code>:<br>
	 * 4: You have successfully blocked access.<br>
	 * 5: The unblocking has been successful.<br>
	 * 6 with Mode 0: You have successfully removed the name from the ranks.<br>
	 * 6 with Mode 1: You have entered an invalid character name.<br>
	 * 16: GM Hide, mode determines whether or not it is on.<br>
	 * 26: Enables minimap<br>
	 * 27: Disables minimap<br>
	 * 0x29, with Mode 0: Unable to send the message. Please enter the user's name before warning.<br>
	 * 0x29, with Mode 1: Your warning has been successfully sent.<br>
	 *
	 * @param type The type
	 * @param mode The mode
	 * @return The gm effect packet
	 */
	public static MaplePacket sendGMOperation(int type, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GM_OPERATION.getValue());
		mplew.write(type);
		mplew.write(mode);
		return mplew.getPacket();
	}

	public static MaplePacket sendHammerSlot(int slot) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.VICIOUS_HAMMER.getValue());
		mplew.write(0x34);
		mplew.writeInt(0);
		mplew.writeInt(slot);
		return mplew.getPacket();
	}

	public static MaplePacket sendHammerEnd() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.VICIOUS_HAMMER.getValue());
		mplew.write(0x38);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket updateHammerItem(IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(0);
		mplew.write(2);
		mplew.write(3);
		mplew.write(item.getType());
		mplew.writeShort(item.getPosition());
		mplew.write(0);
		mplew.write(1);
		addItemInfo(mplew, item, false, false, true);
		return mplew.getPacket();
	}

	public static MaplePacket sendCygnusCreation() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CREATE_CYGNUS.getValue());
		return mplew.getPacket();
	}

	/**
	 *
	 * @param type Success = 0 \r\n Name in Use = 1 \r\n All Character Slots Full = 2 \r\n This Name Cannot be Used = 3 \r\n
	 * @return
	 */
	public static MaplePacket sendCygnusMessage(int type) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CYGNUS_RESPONSE.getValue());
		mplew.writeInt(type);
		return mplew.getPacket();

	}
}
