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
package odinms.net.channel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import odinms.client.MapleCharacter;
import odinms.client.messages.CommandProcessor;
import odinms.database.DatabaseConnection;
import odinms.net.MaplePacket;
import odinms.net.MapleServerHandler;
import odinms.net.PacketProcessor;
import odinms.net.channel.remote.ChannelWorldInterface;
import odinms.net.mina.MapleCodecFactory;
import odinms.net.world.guild.MapleGuild;
import odinms.net.world.guild.MapleGuildCharacter;
import odinms.net.world.guild.MapleGuildSummary;
import odinms.net.world.remote.WorldChannelInterface;
import odinms.net.world.remote.WorldRegistry;
import odinms.provider.MapleDataProviderFactory;
import odinms.scripting.event.EventScriptManager;
import odinms.server.AutobanManager;
import odinms.server.MapleSquad;
import odinms.server.MapleSquadType;
import odinms.server.MapleTrade;
import odinms.server.ShutdownServer;
import odinms.server.TimerManager;
import odinms.server.maps.MapleMapFactory;
import odinms.tools.MaplePacketCreator;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelServer implements Runnable, ChannelServerMBean {

	private static int uniqueID = 1;
	private int port = 7575;
	private static Properties initialProp;
	private static final Logger log = LoggerFactory.getLogger(ChannelServer.class);
	//private static ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(5);
	private static WorldRegistry worldRegistry;
	private PlayerStorage players = new PlayerStorage();
	// private Map<String, MapleCharacter> clients = new LinkedHashMap<String, MapleCharacter>();
	private String serverMessage;
	private int expRate;
	private int mesoRate;
	private int dropRate;
	private int bossdropRate;
	private int petExpRate;
	private boolean gmWhiteText;
	private boolean cashshop;
	private boolean dropUndroppables;
	private boolean moreThanOne;
	private int channel;
	private String key;
	private Properties props = new Properties();
	private ChannelWorldInterface cwi;
	private WorldChannelInterface wci = null;
	private IoAcceptor acceptor;
	private String ip;
	private boolean shutdown = false;
	private boolean finishedShutdown = false;
	private MapleMapFactory mapFactory;
	private EventScriptManager eventSM;
	private static Map<Integer, ChannelServer> instances = new HashMap<Integer, ChannelServer>();
	private static Map<String, ChannelServer> pendingInstances = new HashMap<String, ChannelServer>();
	private Map<Integer, MapleGuildSummary> gsStore = new HashMap<Integer, MapleGuildSummary>();
	private Boolean worldReady = true;
	private Map<MapleSquadType, MapleSquad> mapleSquads = new HashMap<MapleSquadType, MapleSquad>();

	private ChannelServer(String key) {
		mapFactory = new MapleMapFactory(MapleDataProviderFactory.getDataProvider(new File(System.getProperty("odinms.wzpath") + "/Map.wz")), MapleDataProviderFactory.getDataProvider(new File(System.getProperty("odinms.wzpath") + "/String.wz")));
		this.key = key;
	}

	public static WorldRegistry getWorldRegistry() {
		return worldRegistry;
	}

	public void reconnectWorld() {
		// check if the connection is really gone
		try {
			wci.isAvailable();
		} catch (RemoteException ex) {
			synchronized (worldReady) {
				worldReady = false;
			}
			synchronized (cwi) {
				synchronized (worldReady) {
					if (worldReady) {
						return;
					}
				}
				log.warn("Reconnecting to world server");
				synchronized (wci) {
					// completely re-establish the rmi connection
					try {
						initialProp = new Properties();
						FileReader fr = new FileReader(System.getProperty("odinms.channel.config"));
						initialProp.load(fr);
						fr.close();
						Registry registry = LocateRegistry.getRegistry(initialProp.getProperty("odinms.world.host"),
								Registry.REGISTRY_PORT, new SslRMIClientSocketFactory());
						worldRegistry = (WorldRegistry) registry.lookup("WorldRegistry");
						cwi = new ChannelWorldInterfaceImpl(this);
						wci = worldRegistry.registerChannelServer(key, cwi);
						props = wci.getGameProperties();
						expRate = Integer.parseInt(props.getProperty("odinms.world.exp"));
						mesoRate = Integer.parseInt(props.getProperty("odinms.world.meso"));
						dropRate = Integer.parseInt(props.getProperty("odinms.world.drop"));
						bossdropRate = Integer.parseInt(props.getProperty("odinms.world.bossdrop"));
						petExpRate = Integer.parseInt(props.getProperty("odinms.world.petExp"));
						serverMessage = props.getProperty("odinms.world.serverMessage");
						dropUndroppables = Boolean.parseBoolean(props.getProperty("odinms.world.alldrop", "false"));
						moreThanOne = Boolean.parseBoolean(props.getProperty("odinms.world.morethanone", "false"));
						gmWhiteText = Boolean.parseBoolean(props.getProperty("odinms.world.gmWhiteText", "false"));
						cashshop = Boolean.parseBoolean(props.getProperty("odinms.world.cashshop", "false"));
						Properties dbProp = new Properties();
						fr = new FileReader("db.properties");
						dbProp.load(fr);
						fr.close();
						DatabaseConnection.setProps(dbProp);
						DatabaseConnection.getConnection();
						wci.serverReady();
					} catch (Exception e) {
						log.error("Reconnecting failed", e);
					}
					worldReady = true;
				}
			}
			synchronized (worldReady) {
				worldReady.notifyAll();
			}
		}
	}

	@Override
	public void run() {
		try {
			cwi = new ChannelWorldInterfaceImpl(this);
			wci = worldRegistry.registerChannelServer(key, cwi);
			props = wci.getGameProperties();
			expRate = Integer.parseInt(props.getProperty("odinms.world.exp"));
			mesoRate = Integer.parseInt(props.getProperty("odinms.world.meso"));
			dropRate = Integer.parseInt(props.getProperty("odinms.world.drop"));
			bossdropRate = Integer.parseInt(props.getProperty("odinms.world.bossdrop"));
			petExpRate = Integer.parseInt(props.getProperty("odinms.world.petExp"));
			serverMessage = props.getProperty("odinms.world.serverMessage");
			dropUndroppables = Boolean.parseBoolean(props.getProperty("odinms.world.alldrop", "false"));
			moreThanOne = Boolean.parseBoolean(props.getProperty("odinms.world.morethanone", "false"));
			eventSM = new EventScriptManager(this, props.getProperty("odinms.channel.events").split(","));
			gmWhiteText = Boolean.parseBoolean(props.getProperty("odinms.world.gmWhiteText", "false"));
			cashshop = Boolean.parseBoolean(props.getProperty("odinms.world.cashshop", "false"));
			Properties dbProp = new Properties();
			FileReader fileReader = new FileReader("db.properties");
			dbProp.load(fileReader);
			fileReader.close();
			DatabaseConnection.setProps(dbProp);
			DatabaseConnection.getConnection();
			// MapleShopFactory.getInstance().loadAllShops();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		port = Integer.parseInt(props.getProperty("odinms.channel.net.port"));
		ip = props.getProperty("odinms.channel.net.interface") + ":" + port;

		ByteBuffer.setUseDirectBuffers(false);
		ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

		acceptor = new SocketAcceptor();

		SocketAcceptorConfig cfg = new SocketAcceptorConfig();
		// cfg.setThreadModel(ThreadModel.MANUAL); // *fingers crossed*, I hope the executor filter handles everything
		// executor = new ThreadPoolExecutor(16, 16, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		// cfg.getFilterChain().addLast("executor", new ExecutorFilter(executor));
		cfg.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
		cfg.getSessionConfig().setTcpNoDelay(true);
		// Item.loadInitialDataFromDB();
		TimerManager tMan = TimerManager.getInstance();
		tMan.start();
		tMan.register(AutobanManager.getInstance(), 60000);

		try {
			MapleServerHandler serverHandler = new MapleServerHandler(PacketProcessor.getProcessor(PacketProcessor.Mode.CHANNELSERVER), channel);
			acceptor.bind(new InetSocketAddress(port), serverHandler, cfg);
			log.info("Channel {}: Listening on port {}", getChannel(), port);
			wci.serverReady();
			eventSM.init();
		} catch (IOException e) {
			log.error("Binding to port " + port + " failed (ch: " + getChannel() + ")", e);
		}
	}

	public void shutdown() {
		// dc all clients by hand so we get sessionClosed...
		shutdown = true;
		List<CloseFuture> futures = new LinkedList<CloseFuture>();
		Collection<MapleCharacter> allchars = players.getAllCharacters();
		MapleCharacter chrs[] = allchars.toArray(new MapleCharacter[allchars.size()]);
		for (MapleCharacter chr : chrs) {
			if (chr.getTrade() != null) {
				MapleTrade.cancelTrade(chr);
			}
			if (chr.getEventInstance() != null) {
				chr.getEventInstance().playerDisconnected(chr);
			}
			chr.saveToDB(true);
			if (chr.getCheatTracker() != null) {
				chr.getCheatTracker().dispose();
			}
			removePlayer(chr);
		}
		for (MapleCharacter chr : chrs) {
			futures.add(chr.getClient().getSession().close());
		}
		for (CloseFuture future : futures) {
			future.join(500);
		}
		finishedShutdown = true;

		wci = null;
		cwi = null;
	}

	public void unbind() {
		acceptor.unbindAll();
	}

	public boolean hasFinishedShutdown() {
		return finishedShutdown;
	}

	public MapleMapFactory getMapFactory() {
		return mapFactory;
	}

	public static ChannelServer newInstance(String key) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
		ChannelServer instance = new ChannelServer(key);
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		mBeanServer.registerMBean(instance, new ObjectName("odinms.net.channel:type=ChannelServer,name=ChannelServer" + uniqueID++));
		pendingInstances.put(key, instance);
		return instance;
	}

	public static ChannelServer getInstance(int channel) {
		return instances.get(channel);
	}

	public void addPlayer(MapleCharacter chr) {
		players.registerPlayer(chr);
		chr.getClient().getSession().write(MaplePacketCreator.serverMessage(serverMessage));
	}

	public IPlayerStorage getPlayerStorage() {
		return players;
	}

	public void removePlayer(MapleCharacter chr) {
		players.deregisterPlayer(chr);
	}

	public int getConnectedClients() {
		return players.getAllCharacters().size();
	}

	@Override
	public String getServerMessage() {
		return serverMessage;
	}

	@Override
	public void setServerMessage(String newMessage) {
		serverMessage = newMessage;
		broadcastPacket(MaplePacketCreator.serverMessage(serverMessage));
	}

	public void broadcastPacket(MaplePacket data) {
		for (MapleCharacter chr : players.getAllCharacters()) {
			chr.getClient().getSession().write(data);
		}
	}

	@Override
	public int getExpRate() {
		return expRate;
	}

	@Override
	public void setExpRate(int expRate) {
		this.expRate = expRate;
	}

	public int getChannel() {
		return channel;
	}

	public void setChannel(int channel) {
		if (pendingInstances.containsKey(key)) {
			pendingInstances.remove(key);
		}
		if (instances.containsKey(channel)) {
			instances.remove(channel);
		}
		instances.put(channel, this);
		this.channel = channel;
		this.mapFactory.setChannel(channel);
	}

	public static Collection<ChannelServer> getAllInstances() {
		return Collections.unmodifiableCollection(instances.values());
	}

	public String getIP() {
		return ip;
	}

	public String getIP(int channel) {
		try {
			return getWorldInterface().getIP(channel);
		} catch (RemoteException e) {
			log.error("Lost connection to world server", e);
			throw new RuntimeException("Lost connection to world server");
		}
	}

	public WorldChannelInterface getWorldInterface() {
		synchronized (worldReady) {
			while (!worldReady) {
				try {
					worldReady.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		return wci;
	}

	public String getProperty(String name) {
		return props.getProperty(name);
	}

	public boolean isShutdown() {
		return shutdown;
	}

	@Override
	public void shutdown(int time) {
		broadcastPacket(MaplePacketCreator.serverNotice(0, "The world will be shut down in " + (time / 60000) + " minutes, please log off safely"));
		TimerManager.getInstance().schedule(new ShutdownServer(getChannel()), time);
	}

	@Override
	public void shutdownWorld(int time) {
		try {
			getWorldInterface().shutdown(time);
		} catch (RemoteException e) {
			reconnectWorld();
		}
	}

	public int getLoadedMaps() {
		return mapFactory.getLoadedMaps();
	}

	public EventScriptManager getEventSM() {
		return eventSM;
	}

	public void reloadEvents() {
		eventSM.cancel();
		eventSM = new EventScriptManager(this, props.getProperty("odinms.channel.events").split(","));
		eventSM.init();
	}

	@Override
	public int getMesoRate() {
		return mesoRate;
	}

	@Override
	public void setMesoRate(int mesoRate) {
		this.mesoRate = mesoRate;
	}

	@Override
	public int getDropRate() {
		return dropRate;
	}

	@Override
	public void setDropRate(int dropRate) {
		this.dropRate = dropRate;
	}

	@Override
	public int getBossDropRate() {
		return bossdropRate;
	}

	@Override
	public void setBossDropRate(int bossdropRate) {
		this.bossdropRate = bossdropRate;
	}

	@Override
	public int getPetExpRate() {
		return petExpRate;
	}

	@Override
	public void setPetExpRate(int petExpRate) {
		this.petExpRate = petExpRate;
	}

	public boolean allowUndroppablesDrop() {
		return dropUndroppables;
	}

	public boolean allowMoreThanOne() {
		return moreThanOne;
	}

	public boolean allowGmWhiteText() {
		return gmWhiteText;
	}

	public boolean allowCashshop() {
		return cashshop;
	}

	public MapleGuild getGuild(MapleGuildCharacter mgc) {
		int gid = mgc.getGuildId();
		MapleGuild g = null;
		try {
			g = this.getWorldInterface().getGuild(gid, mgc);
		} catch (RemoteException re) {
			log.error("RemoteException while fetching MapleGuild.", re);
			return null;
		}

		if (gsStore.get(gid) == null) {
			gsStore.put(gid, new MapleGuildSummary(g));
		}
		return g;
	}

	public MapleGuildSummary getGuildSummary(int gid) {
		if (gsStore.containsKey(gid)) {
			return gsStore.get(gid);
		} else {		//this shouldn't happen much, if ever, but if we're caught
			//without the summary, we'll have to do a worldop

			try {
				MapleGuild g = this.getWorldInterface().getGuild(gid, null);
				if (g != null) {
					gsStore.put(gid, new MapleGuildSummary(g));
				}
				return gsStore.get(gid);	//if g is null, we will end up returning null

			} catch (RemoteException re) {
				log.error("RemoteException while fetching GuildSummary.", re);
				return null;
			}
		}
	}

	public void updateGuildSummary(int gid, MapleGuildSummary mgs) {
		gsStore.put(gid, mgs);
	}

	public void reloadGuildSummary() {
		try {
			MapleGuild g;
			for (int i : gsStore.keySet()) {
				g = this.getWorldInterface().getGuild(i, null);
				if (g != null) {
					gsStore.put(i, new MapleGuildSummary(g));
				} else {
					gsStore.remove(i);
				}
			}
		} catch (RemoteException re) {
			log.error("RemoteException while reloading GuildSummary.", re);
		}
	}

	public static void main(String args[]) throws FileNotFoundException, IOException, NotBoundException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
		initialProp = new Properties();
		initialProp.load(new FileReader(System.getProperty("odinms.channel.config")));
		Registry registry = LocateRegistry.getRegistry(initialProp.getProperty("odinms.world.host"), Registry.REGISTRY_PORT, new SslRMIClientSocketFactory());
		worldRegistry = (WorldRegistry) registry.lookup("WorldRegistry");
		for (int i = 0; i < Integer.parseInt(initialProp.getProperty("odinms.channel.count", "0")); i++) {
			newInstance(initialProp.getProperty("odinms.channel." + i + ".key")).run();
		}
		DatabaseConnection.getConnection(); // touch - so we see database problems early...

		CommandProcessor.registerMBean();
	}

	public MapleSquad getMapleSquad(MapleSquadType type) {
		return mapleSquads.get(type);
	}

	public boolean addMapleSquad(MapleSquad squad, MapleSquadType type) {
		if (mapleSquads.get(type) == null) {
			mapleSquads.remove(type);
			mapleSquads.put(type, squad);
			return true;
		} else {
			return false;
		}
	}

	public boolean removeMapleSquad(MapleSquad squad, MapleSquadType type) {
		if (mapleSquads.containsKey(type)) {
			if (mapleSquads.get(type) == squad) {
				mapleSquads.remove(type);
				return true;
			}
		}
		return false;
	}
}