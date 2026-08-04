package io.github.skyblueheat.echoclaims.testplugin;

import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HeadlessBotClient {

    private final String host;
    private final int port;
    private final String username;
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private final CountDownLatch disconnectedLatch = new CountDownLatch(1);
    private final AtomicBoolean joined = new AtomicBoolean(false);
    private volatile ClientSession session;

    public HeadlessBotClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public void connect() throws Exception {
        MinecraftProtocol protocol = new MinecraftProtocol(username);

        SessionService sessionService = new SessionService();

        session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setProtocol(protocol)
                .create();
        session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);

        session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                if (packet instanceof ClientboundLoginPacket) {
                    joined.set(true);
                    connectedLatch.countDown();
                    System.out.println("[HeadlessBot] Received login packet - player joined the game");
                } else if (packet instanceof ClientboundPlayerCombatKillPacket) {
                    System.out.println("[HeadlessBot] Received death screen - sending respawn request");
                    session.send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
                } else if (packet instanceof ClientboundRespawnPacket) {
                    System.out.println("[HeadlessBot] Received respawn packet - player respawned");
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                System.out.println("[HeadlessBot] Disconnected: " + event.getReason());
                disconnectedLatch.countDown();
            }
        });

        session.connect();
    }

    public boolean awaitJoin(long timeout, TimeUnit unit) throws InterruptedException {
        return connectedLatch.await(timeout, unit);
    }

    public boolean awaitDisconnect(long timeout, TimeUnit unit) throws InterruptedException {
        return disconnectedLatch.await(timeout, unit);
    }

    public boolean hasJoined() {
        return joined.get();
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect("Validation complete");
        }
    }

    public String getProtocolVersion() {
        return MinecraftCodec.CODEC.getProtocolVersion() + " (" + MinecraftCodec.CODEC.getMinecraftVersion() + ")";
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25565;
        String username = args.length > 2 ? args[2] : "TestBot";

        HeadlessBotClient bot = new HeadlessBotClient(host, port, username);
        System.out.println("[HeadlessBot] Connecting to " + host + ":" + port + " as " + username);
        System.out.println("[HeadlessBot] Protocol: " + bot.getProtocolVersion());
        bot.connect();

        if (bot.awaitJoin(30, TimeUnit.SECONDS)) {
            System.out.println("[HeadlessBot] Successfully joined the server");
        } else {
            System.out.println("[HeadlessBot] Failed to join within 30 seconds");
            System.exit(1);
        }

        bot.awaitDisconnect(600, TimeUnit.SECONDS);
        System.out.println("[HeadlessBot] Session ended");
    }
}
