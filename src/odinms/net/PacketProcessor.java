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
package odinms.net;

import odinms.net.login.handler.CharlistRequestHandler;
import odinms.net.login.handler.DeleteCharHandler;
import odinms.net.login.handler.CheckCharNameHandler;
import odinms.net.login.handler.ServerStatusRequestHandler;
import odinms.net.login.handler.LoginPasswordHandler;
import odinms.net.login.handler.CharSelectedHandler;
import odinms.net.login.handler.CreateCharHandler;
import odinms.net.login.handler.AfterLoginHandler;
import odinms.net.login.handler.RelogRequestHandler;
import odinms.net.login.handler.ServerlistRequestHandler;
import odinms.net.channel.handler.SkillEffectHandler;
import odinms.net.channel.handler.NPCAnimation;
import odinms.net.channel.handler.EnergyAttackHandler;
import odinms.net.channel.handler.MoveLifeHandler;
import odinms.net.channel.handler.NPCShopHandler;
import odinms.net.channel.handler.QuestActionHandler;
import odinms.net.channel.handler.CloseRangeDamageHandler;
import odinms.net.channel.handler.NPCMoreTalkHandler;
import odinms.net.channel.handler.DistributeAPHandler;
import odinms.net.channel.handler.TouchingCashShopHandler;
import odinms.net.channel.handler.UseChairHandler;
import odinms.net.channel.handler.NoteActionHandler;
import odinms.net.channel.handler.CharInfoRequestHandler;
import odinms.net.channel.handler.MagicDamageHandler;
import odinms.net.channel.handler.WhisperHandler;
import odinms.net.channel.handler.SkillBookHandler;
import odinms.net.channel.handler.ChangeMapHandler;
import odinms.net.channel.handler.CreateCygnusHandler;
import odinms.net.channel.handler.MoveSummonHandler;
import odinms.net.channel.handler.ItemMoveHandler;
import odinms.net.channel.handler.GuildOperationHandler;
import odinms.net.channel.handler.PartychatHandler;
import odinms.net.channel.handler.ViciousHammerHandler;
import odinms.net.channel.handler.EnterMTSHandler;
import odinms.net.channel.handler.MonsterBombHandler;
import odinms.net.channel.handler.UseItemEffectHandler;
import odinms.net.channel.handler.PetFoodHandler;
import odinms.net.channel.handler.UseItemHandler;
import odinms.net.channel.handler.NPCTalkHandler;
import odinms.net.channel.handler.MovePetHandler;
import odinms.net.channel.handler.ItemPickupHandler;
import odinms.net.channel.handler.BBSOperationHandler;
import odinms.net.channel.handler.GiveFameHandler;
import odinms.net.channel.handler.BuyCSItemHandler;
import odinms.net.channel.handler.AutoAssignHandler;
import odinms.net.channel.handler.HealOvertimeHandler;
import odinms.net.channel.handler.SpecialMoveHandler;
import odinms.net.channel.handler.PlayerLoggedinHandler;
import odinms.net.channel.handler.DistributeSPHandler;
import odinms.net.channel.handler.ChangeChannelHandler;
import odinms.net.channel.handler.MessengerHandler;
import odinms.net.channel.handler.PetCommandHandler;
import odinms.net.channel.handler.EnterCashShopHandler;
import odinms.net.channel.handler.UseSummonBag;
import odinms.net.channel.handler.UseCashItemHandler;
import odinms.net.channel.handler.SpawnPetHandler;
import odinms.net.channel.handler.ReactorHitHandler;
import odinms.net.channel.handler.AutoAggroHandler;
import odinms.net.channel.handler.TakeDamageHandler;
import odinms.net.channel.handler.CancelChairHandler;
import odinms.net.channel.handler.MesoDropHandler;
import odinms.net.channel.handler.DamageSummonHandler;
import odinms.net.channel.handler.CancelBuffHandler;
import odinms.net.channel.handler.DenyPartyRequestHandler;
import odinms.net.channel.handler.PartyOperationHandler;
import odinms.net.channel.handler.ScrollHandler;
import odinms.net.channel.handler.BuddylistModifyHandler;
import odinms.net.channel.handler.PetChatHandler;
import odinms.net.channel.handler.PlayerInteractionHandler;
import odinms.net.channel.handler.SkillMacroHandler;
import odinms.net.channel.handler.CancelDebuffHandler;
import odinms.net.channel.handler.FaceExpressionHandler;
import odinms.net.channel.handler.ChangeMapSpecialHandler;
import odinms.net.channel.handler.CancelItemEffectHandler;
import odinms.net.channel.handler.KeymapChangeHandler;
import odinms.net.channel.handler.DoorHandler;
import odinms.net.channel.handler.StorageHandler;
import odinms.net.channel.handler.SummonDamageHandler;
import odinms.net.channel.handler.GeneralChatHandler;
import odinms.net.channel.handler.CouponCodeHandler;
import odinms.net.channel.handler.MovePlayerHandler;
import odinms.net.channel.handler.RangedAttackHandler;
import odinms.net.handler.KeepAliveHandler;
import odinms.net.handler.LoginRequiringNoOpHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PacketProcessor {

	private static Logger log = LoggerFactory.getLogger(PacketProcessor.class);

	public enum Mode {

		LOGINSERVER,
		CHANNELSERVER
	};
	private static PacketProcessor instance;
	private MaplePacketHandler[] handlers;

	private PacketProcessor() {
		int maxRecvOp = 0;
		for (RecvPacketOpcode op : RecvPacketOpcode.values()) {
			if (op.getValue() > maxRecvOp) {
				maxRecvOp = op.getValue();
			}
		}
		handlers = new MaplePacketHandler[maxRecvOp + 1];
	}

	public MaplePacketHandler getHandler(short packetId) {
		if (packetId > handlers.length) {
			return null;
		}
		MaplePacketHandler handler = handlers[packetId];
		if (handler != null) {
			return handler;
		}
		return null;
	}

	public void registerHandler(RecvPacketOpcode code, MaplePacketHandler handler) {
		try {
			handlers[code.getValue()] = handler;
		} catch (ArrayIndexOutOfBoundsException aiobe) {
			log.error("Missing OpCode - " + code.name());
		}
	}

	public synchronized static PacketProcessor getProcessor(Mode mode) {
		if (instance == null) {
			instance = new PacketProcessor();
			instance.reset(mode);
		}
		return instance;
	}

	public void reset(Mode mode) {
		handlers = new MaplePacketHandler[handlers.length];
		registerHandler(RecvPacketOpcode.PONG, new KeepAliveHandler());
		if (mode == Mode.LOGINSERVER) {
			registerHandler(RecvPacketOpcode.AFTER_LOGIN, new AfterLoginHandler());
			registerHandler(RecvPacketOpcode.SERVERLIST_REREQUEST, new ServerlistRequestHandler());
			registerHandler(RecvPacketOpcode.CHARLIST_REQUEST, new CharlistRequestHandler());
			registerHandler(RecvPacketOpcode.CHAR_SELECT, new CharSelectedHandler());
			registerHandler(RecvPacketOpcode.LOGIN_PASSWORD, new LoginPasswordHandler());
			registerHandler(RecvPacketOpcode.RELOG, new RelogRequestHandler());
			registerHandler(RecvPacketOpcode.SERVERLIST_REQUEST, new ServerlistRequestHandler());
			registerHandler(RecvPacketOpcode.SERVERSTATUS_REQUEST, new ServerStatusRequestHandler());
			registerHandler(RecvPacketOpcode.CHECK_CHAR_NAME, new CheckCharNameHandler());
			registerHandler(RecvPacketOpcode.CREATE_CHAR, new CreateCharHandler());
			registerHandler(RecvPacketOpcode.DELETE_CHAR, new DeleteCharHandler());
		} else if (mode == Mode.CHANNELSERVER) {
			registerHandler(RecvPacketOpcode.CHANGE_CHANNEL, new ChangeChannelHandler());
			registerHandler(RecvPacketOpcode.STRANGE_DATA, LoginRequiringNoOpHandler.getInstance());
			registerHandler(RecvPacketOpcode.GENERAL_CHAT, new GeneralChatHandler());
			registerHandler(RecvPacketOpcode.WHISPER, new WhisperHandler());
			registerHandler(RecvPacketOpcode.NPC_TALK, new NPCTalkHandler());
			registerHandler(RecvPacketOpcode.NPC_TALK_MORE, new NPCMoreTalkHandler());
			registerHandler(RecvPacketOpcode.QUEST_ACTION, new QuestActionHandler());
			registerHandler(RecvPacketOpcode.NPC_SHOP, new NPCShopHandler());
			registerHandler(RecvPacketOpcode.ITEM_MOVE, new ItemMoveHandler());
			registerHandler(RecvPacketOpcode.MESO_DROP, new MesoDropHandler());
			registerHandler(RecvPacketOpcode.PLAYER_LOGGEDIN, new PlayerLoggedinHandler());
			registerHandler(RecvPacketOpcode.CHANGE_MAP, new ChangeMapHandler());
			registerHandler(RecvPacketOpcode.MOVE_LIFE, new MoveLifeHandler());
			registerHandler(RecvPacketOpcode.CLOSE_RANGE_ATTACK, new CloseRangeDamageHandler());
			registerHandler(RecvPacketOpcode.RANGED_ATTACK, new RangedAttackHandler());
			registerHandler(RecvPacketOpcode.MAGIC_ATTACK, new MagicDamageHandler());
			registerHandler(RecvPacketOpcode.ENERGY_CHARGE_ATTACK, new EnergyAttackHandler());
			registerHandler(RecvPacketOpcode.TAKE_DAMAGE, new TakeDamageHandler());
			registerHandler(RecvPacketOpcode.MOVE_PLAYER, new MovePlayerHandler());
			registerHandler(RecvPacketOpcode.USE_CASH_ITEM, new UseCashItemHandler());
			registerHandler(RecvPacketOpcode.USE_ITEM, new UseItemHandler());
			registerHandler(RecvPacketOpcode.USE_RETURN_SCROLL, new UseItemHandler());
			registerHandler(RecvPacketOpcode.USE_UPGRADE_SCROLL, new ScrollHandler());
			registerHandler(RecvPacketOpcode.USE_SUMMON_BAG, new UseSummonBag());
			registerHandler(RecvPacketOpcode.FACE_EXPRESSION, new FaceExpressionHandler());
			registerHandler(RecvPacketOpcode.HEAL_OVER_TIME, new HealOvertimeHandler());
			registerHandler(RecvPacketOpcode.ITEM_PICKUP, new ItemPickupHandler());
			registerHandler(RecvPacketOpcode.CHAR_INFO_REQUEST, new CharInfoRequestHandler());
			registerHandler(RecvPacketOpcode.SPECIAL_MOVE, new SpecialMoveHandler());
			registerHandler(RecvPacketOpcode.CANCEL_BUFF, new CancelBuffHandler());
			registerHandler(RecvPacketOpcode.CANCEL_ITEM_EFFECT, new CancelItemEffectHandler());
			registerHandler(RecvPacketOpcode.PLAYER_INTERACTION, new PlayerInteractionHandler());
			registerHandler(RecvPacketOpcode.DISTRIBUTE_AP, new DistributeAPHandler());
			registerHandler(RecvPacketOpcode.DISTRIBUTE_SP, new DistributeSPHandler());
			registerHandler(RecvPacketOpcode.AUTO_ASSIGN, new AutoAssignHandler());
			registerHandler(RecvPacketOpcode.CHANGE_KEYMAP, new KeymapChangeHandler());
			registerHandler(RecvPacketOpcode.CHANGE_MAP_SPECIAL, new ChangeMapSpecialHandler());
			registerHandler(RecvPacketOpcode.STORAGE, new StorageHandler());
			registerHandler(RecvPacketOpcode.GIVE_FAME, new GiveFameHandler());
			registerHandler(RecvPacketOpcode.PARTY_OPERATION, new PartyOperationHandler());
			registerHandler(RecvPacketOpcode.DENY_PARTY_REQUEST, new DenyPartyRequestHandler());
			registerHandler(RecvPacketOpcode.PARTYCHAT, new PartychatHandler());
			registerHandler(RecvPacketOpcode.USE_DOOR, new DoorHandler());
			registerHandler(RecvPacketOpcode.ENTER_MTS, new EnterMTSHandler());
			registerHandler(RecvPacketOpcode.ENTER_CASH_SHOP, new EnterCashShopHandler());
			registerHandler(RecvPacketOpcode.DAMAGE_SUMMON, new DamageSummonHandler());
			registerHandler(RecvPacketOpcode.MOVE_SUMMON, new MoveSummonHandler());
			registerHandler(RecvPacketOpcode.SUMMON_ATTACK, new SummonDamageHandler());
			registerHandler(RecvPacketOpcode.BUDDYLIST_MODIFY, new BuddylistModifyHandler());
			registerHandler(RecvPacketOpcode.USE_ITEMEFFECT, new UseItemEffectHandler());
			registerHandler(RecvPacketOpcode.USE_CHAIR, new UseChairHandler());
			registerHandler(RecvPacketOpcode.CANCEL_CHAIR, new CancelChairHandler());
			registerHandler(RecvPacketOpcode.DAMAGE_REACTOR, new ReactorHitHandler());
			registerHandler(RecvPacketOpcode.GUILD_OPERATION, new GuildOperationHandler());
			registerHandler(RecvPacketOpcode.BBS_OPERATION, new BBSOperationHandler());
			registerHandler(RecvPacketOpcode.SKILL_EFFECT, new SkillEffectHandler());
			registerHandler(RecvPacketOpcode.MESSENGER, new MessengerHandler());
			registerHandler(RecvPacketOpcode.NPC_ACTION, new NPCAnimation());
			registerHandler(RecvPacketOpcode.TOUCHING_CS, new TouchingCashShopHandler());
			registerHandler(RecvPacketOpcode.BUY_CS_ITEM, new BuyCSItemHandler());
			registerHandler(RecvPacketOpcode.COUPON_CODE, new CouponCodeHandler());
			registerHandler(RecvPacketOpcode.SPAWN_PET, new SpawnPetHandler());
			registerHandler(RecvPacketOpcode.MOVE_PET, new MovePetHandler());
			registerHandler(RecvPacketOpcode.PET_CHAT, new PetChatHandler());
			registerHandler(RecvPacketOpcode.PET_COMMAND, new PetCommandHandler());
			registerHandler(RecvPacketOpcode.PET_FOOD, new PetFoodHandler());
			registerHandler(RecvPacketOpcode.AUTO_AGGRO, new AutoAggroHandler());
			registerHandler(RecvPacketOpcode.MONSTER_BOMB, new MonsterBombHandler());
			registerHandler(RecvPacketOpcode.CANCEL_DEBUFF, new CancelDebuffHandler());
			registerHandler(RecvPacketOpcode.USE_SKILL_BOOK, new SkillBookHandler());
			registerHandler(RecvPacketOpcode.SKILL_MACRO, new SkillMacroHandler());
			registerHandler(RecvPacketOpcode.NOTE_ACTION, new NoteActionHandler());
			registerHandler(RecvPacketOpcode.VICIOUS_HAMMER, new ViciousHammerHandler());
			registerHandler(RecvPacketOpcode.CREATE_CYGNUS, new CreateCygnusHandler());
		} else {
			throw new RuntimeException("Unknown packet processor mode");
		}
	}
}
