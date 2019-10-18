package eu.brain.iot.installer.api;

import java.util.List;
import java.util.Map;

import org.osgi.util.promise.Promise;

import aQute.bnd.http.HttpClient;

public interface FunctionInstaller {
	
	Promise<InstallResponseDTO> installFunction(String symbolicName, String version, List<String> indexes, 
    		List<String> requirements, HttpClient client);

	Promise<InstallResponseDTO> updateFunction(String oldSymbolicName, String oldVersion, 
    		String newSymbolicName, String newVersion, List<String> indexes, 
    		List<String> requirements, HttpClient client);

	Promise<InstallResponseDTO> uninstallFunction(String symbolicName, String version);
	
	Map<String, String> listInstalledFunctions();
	
	Promise<InstallResponseDTO> resetNode();

}
