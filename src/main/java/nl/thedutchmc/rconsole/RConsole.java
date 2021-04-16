package nl.thedutchmc.rconsole;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;

import nl.thedutchmc.rconsole.config.Configuration;
import nl.thedutchmc.rconsole.gson.out.ServerShutdownPacket;
import nl.thedutchmc.rconsole.tcp.TcpClient;
import nl.thedutchmc.rconsole.tcp.TcpServer;

public class RConsole extends JavaPlugin {

	private static RConsole INSTANCE;
	private static volatile boolean DEBUG = false;
	private static volatile boolean IS_RUNNING = true;
	
	private TcpServer tcpServer;
	
	@Override
	public void onEnable() {
		RConsole.INSTANCE = this;
		
		Configuration config = new Configuration(this);
		RConsole.DEBUG = config.getConfig().isDebugMode();
		
		this.tcpServer = new TcpServer(config.getConfig().getListenPort(), config.getConfig().getTokens(), this);
		new Thread(tcpServer, "rConsole-TCPServer-Thread").start();		
	}
	
	@Override
	public void onDisable() {
		
		String packetSerialized = new Gson().toJson(new ServerShutdownPacket());
		for(TcpClient client : this.tcpServer.getSignedInClients()) {
			RConsole.logDebug(String.format("Disconnecting client '%s'", client.getName()));
			Socket socket = client.getSocket();
			
			if(socket.isClosed()) {
				continue;
			}
			
			try {
				PrintWriter pw = new PrintWriter(socket.getOutputStream());
				pw.println(packetSerialized);
				pw.flush();
				pw.close();
			} catch(IOException e) {
			}
		}
	}
	
	public static void logInfo(Object log) {
		RConsole.INSTANCE.getLogger().info(log.toString());
	}
	
	public static void logWarn(Object log) {
		RConsole.INSTANCE.getLogger().warning(log.toString());
	}
	
	public static void logDebug(Object log) {
		if(!RConsole.DEBUG) return;
		
		RConsole.INSTANCE.getLogger().info("[DEBUG] " + log.toString());
	}
	
	public static boolean getIsRunning() {
		return RConsole.IS_RUNNING;
	}
}
