package nl.thedutchmc.rconsole.core.webserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

import com.google.gson.Gson;

import net.lingala.zip4j.ZipFile;
import nl.thedutchmc.rconsole.core.RConsole;
import nl.thedutchmc.rconsole.core.annotations.Nullable;
import nl.thedutchmc.rconsole.core.util.Util;

/**
 * Wrapper class around {@link Native}
 * @author Tobias de Bruijn
 *
 */
public class WebServer {

	private static boolean LIB_LOADED = false;
	private static RConsole rconsole;
	private static Consumer<String> consoleCommandExecutor;
	
	public WebServer(RConsole rconsole, Consumer<String> consoleCommandExecutor) {
		WebServer.rconsole = rconsole;
		WebServer.consoleCommandExecutor = consoleCommandExecutor;
	}
	
	static {
		saveNativeLib: {
			String osString = System.getProperty("os.name").toLowerCase();
			String nativeLibraryName;
			
			//Determine the OS, and from that get the path inside the jar file to librconsole
			if(osString.contains("linux") ) {
				nativeLibraryName = "/x86_64/linux/librconsole.so";
			} else if(osString.contains("windows")) {
				nativeLibraryName = "/x86_64/windows/librconsole.dll";
			} else if(osString.contains("mac")) {
				nativeLibraryName = "/x86_64/macos/librconsole.dylib";
			} else {
				rconsole.logWarn(String.format("You are running on an OS not supported by rConsole (%s). The built-in webserver will not work.", osString));
				break saveNativeLib;
			}
		
			URL libUrl = WebServer.class.getResource(nativeLibraryName);
		
			//Create a temporary directory in which we save librconsole
			File tmpDir;
			try {
				tmpDir = Files.createTempDirectory("librconsole").toFile();
			} catch(IOException e) {
				rconsole.logWarn("An error occurred while creating a temporary directory for 'librconsole': " + e.getMessage());
				rconsole.logDebug(Util.getStackTrace(e));
				
				break saveNativeLib;
			}
			
			//Get only 'librconsole' and the file extension, since that is the name we want the file to have on disk
			String[] nativeLibNameParts = nativeLibraryName.split(Pattern.quote("/"));
			File libTmpFile = new File(tmpDir, nativeLibNameParts[nativeLibNameParts.length-1]);
			libTmpFile.deleteOnExit();
			
			//Copy the file from the jar to the temporary file on disk
			try {
				InputStream in = libUrl.openStream();
				Files.copy(in, libTmpFile.toPath());
			} catch(IOException e) {
				rconsole.logWarn("An error occurred while saving 'librconsole': " + e.getMessage());
				rconsole.logDebug(Util.getStackTrace(e));
				tmpDir.delete();
				break saveNativeLib;
			}
			
			//Finally, try to load the library
			try {
				System.load(libTmpFile.getAbsolutePath());
			} catch(UnsatisfiedLinkError e) {
				rconsole.logWarn("An error occurred while loading 'librconsole': " + e.getMessage());
				rconsole.logDebug(Util.getStackTrace(e));
				tmpDir.delete();
				break saveNativeLib;
			}

			LIB_LOADED = true;
		}
	}
	
	/**
	 * Start the web server<br>
	 * This is a blocking method.
	 */
	public void startWebServer(BiConsumer<String, Boolean> saveResource, File dataFolder) {
		if(!LIB_LOADED) {
			rconsole.logWarn("Unable to start native webserver because the native library 'librconsole' is not loaded");
			return;
		}
		
		//Create the configuration folder for librconsole if it doesn't exist.
		File librconsoleConfigFolder = new File(dataFolder + File.separator + "librconsole");
		if(!librconsoleConfigFolder.exists()) {
			librconsoleConfigFolder.mkdirs();
		}
		
		//Class describing the configuration for librconsole
		//It's values come from the plugin's main configuration
		final class LibrconsoleConfig {
			//The variables in here aren't actually unused, they're serialized by Yaml
			
			@SuppressWarnings("unused")
			public int port = WebServer.rconsole.getConfig().getLibrconsolePort();
			
			@SuppressWarnings("unused")
			public String pepper = WebServer.rconsole.getConfig().getPepper();
		}
		
		//Serialize the LibrconsoleConfig object to a Yaml String
		PropertyUtils propUtils = new PropertyUtils();
		propUtils.setAllowReadOnlyProperties(true);
		Representer repr = new Representer();
		repr.setPropertyUtils(propUtils);
		
		final Yaml yaml = new Yaml(new Constructor(), repr);
		String librConsoleConfigSerialized = yaml.dump(new LibrconsoleConfig());
		
		//Write the String to $server/plugins/rConsole/librconsole/config.yml
		File librconsoleConfigFile = new File(librconsoleConfigFolder, "config.yml");
		try {
			librconsoleConfigFile.delete();
			BufferedWriter bw = new BufferedWriter(new FileWriter(librconsoleConfigFile));
			bw.write(librConsoleConfigSerialized);
			bw.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

		//Get the 'static' folder, in which the website lives.
		//$server/plugins/rConsole/librconsole/static/
		File staticFilesFolder = new File(librconsoleConfigFolder + File.separator + "static");
		
		//Delete and recreate the static folder.
		//We want to do this because the files are likely to change if the 
		//plugin is updated. This way we make sure the files are
		//always up-to-date.
		if(staticFilesFolder.exists()) {
			try {
				Files.walk(staticFilesFolder.toPath())
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		//Recreate the folder
		staticFilesFolder.mkdirs();
				
		//Serialize the website config to a JSON String
		//The WebConfig class is defined below this method.
		final Gson gson = new Gson();
		String webConfigSerialized = gson.toJson(new WebConfig());
		
		//Write the JSON String to $server/plugins/rConsole/librconsole/static/rconsole_web_config.json
		try {
			File webConfigFile = new File(staticFilesFolder, "rconsole_web_config.json");
			if(webConfigFile.exists()) {
				webConfigFile.delete();
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(webConfigFile));
			bw.write(webConfigSerialized);
			bw.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		//We always want to re-extract the zip file, because if there is an update to the plugin
		//the static web files also need to be updated. By deleting it every time,
		//we can guarantee that the files are always up to date.
		saveResource.accept("dist.zip", true);
		try {
			File finalDistZipFile = new File(staticFilesFolder, "dist.zip");
			Files.move(new File(dataFolder, "dist.zip").toPath(), finalDistZipFile.toPath());
			new ZipFile(finalDistZipFile).extractAll(staticFilesFolder.getAbsolutePath());
			
			finalDistZipFile.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Database file
		//$server/plugins/rConsole/librconsole/librconsole.db3
		File librconsoleDatabaseFile = new File(librconsoleConfigFolder, "librconsole.db3");
		
		//Finally, start the webserver in librconsole
		//This is a blocking method call.
		Native.startWebServer(librconsoleConfigFile.getAbsolutePath(), librconsoleDatabaseFile.getAbsolutePath(), staticFilesFolder.getAbsolutePath());
	}
	
	/**Class describing the configuration for the website<br>
	 * It's values come from the plugin's main configuration<br>
	 * <br>
	 * This is not a local class to {@link WebServer#startWebServer()} because of limitations in Gson
	 * 
	 * @see <a href="https://github.com/google/gson/issues/1595"> Gson issue 1595 on GitHub</a>
	 */
	final class WebConfig {
		//The variable isn't actually unused, it's serialized by Gson
		
		//Private constructor so the class cannot be instatiated in a different class than WebServer
		private WebConfig() {}
		
		@SuppressWarnings("unused")
		private String uri = WebServer.rconsole.getConfig().getBaseUrl();
	}
	
	/**
	 * Append a log entry<br>
	 * This will spawn a new Thread
	 * @param log The message of the log entry
	 * @param timestamp The timestamp of when the log occurred
	 * @param level The level at which the log occurred, INFO or WARN
	 * @param thread The thread from which the log originated
	 */
	public void log(String log, long timestamp, String level, String thread) {
		if(!LIB_LOADED) {
			return;
		}
		
		//We put this on a new thread, since it is possible that the lock of the Database object on the rust side isn't free,
		//Then this call will block, and because logging happens quite frequently, and we don't want to hold it up,
		//we put it on a different thread.
		new Thread(new Runnable() {
			@Override
			public void run() {
				Native.appendConsoleLog(log, timestamp, level, thread);
			}
		}).start();
	}
	
	/**
	 * Add a user
	 * @param username The username of the user
	 * @param password The password of the user
	 */
	//TODO this should return a Boolean indicating if the creation was successful
	public void addUser(String username, String password) {
		if(!LIB_LOADED) {
			return;
		}
		
		Native.addUser(username, password);
	}
	
	/**
	 * Delete a user
	 * @param username The username of the user to delete
	 * @return returns true if the deletion was successful, false if the user did not exist or null if an error occurred
	 */
	@Nullable
	public Boolean delUser(String username) {
		if(!LIB_LOADED) {
			return null;
		}
		
		return Native.delUser(username);
	}
	
	/**
	 * Get a list of existing usernames
	 * @return Returns the usernames as a String[], or null if an error occurred
	 */
	@Nullable
	public String[] listUsers() {
		if(!LIB_LOADED) {
			return null;
		}
		
		return Native.listUsers();
	}
	
	/**
	 * Get a HashMap with the username and sessions associated with that username
	 * @return Returns the HashMap, or null if an error occurred
	 */
	@Nullable
	public HashMap<String, String[]> getUserSessions() {
		if(!LIB_LOADED) {
			return null;
		}
		
		return Native.getUserSessions();
	}
	
	/**
	 * Delete a session
	 * @param sessionId The ID of the session to delete
	 * @return returns true if the session was deleted successfully, false if the session did not exist or null if an error occurred
	 */
	@Nullable
	public Boolean delSession(String sessionId) {
		if(!LIB_LOADED) {
			return null;
		}
		
		return Native.delSession(sessionId);
	}
	
	/**
	 * Execute a command<br>
	 * This is a wrapper method around {@link Bukkit#dispatchCommand(org.bukkit.command.CommandSender, String)}, this is because we need to sync with the main server thread.<br>
	 * This method is for native methods to call.
	 * @param cmd The command to execute
	 */
	//Private method because you shouldn't call it from java, and native code does not care about visibility modifiers.
	//The function is used, but again, from native code
	//
	//In theory we don't need this method, but reimplementing this with JNI is a massive pain
	@SuppressWarnings("unused")
	private static void execCommand(String cmd) {
		//We're doing a try/catch on all Exceptions, because if an exception occurs and it is not caught in Java code,
		//but is caught in native code instead, it could crash the JVM
		try {
			Runnable sync = new Runnable() {
				
				@Override
				public void run() {
					consoleCommandExecutor.accept(cmd);
				}
			};
			
			WebServer.rconsole.execSync(sync);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
