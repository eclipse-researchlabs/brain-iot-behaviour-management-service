package eu.brain.iot.installer.api;

import java.util.List;
import java.util.Map;

import org.osgi.util.promise.Promise;

public interface FunctionInstaller {
	
	Promise<InstallResponseDTO> installFunction(String symbolicName, String version, List<String> indexes, List<String> requirements);

	Promise<InstallResponseDTO> updateFunction(String oldSymbolicName, String oldVersion, 
    		String newSymbolicName, String newVersion, List<String> indexes, List<String> requirements);

	Promise<InstallResponseDTO> uninstallFunction(String symbolicName, String version);
	
	Map<String, String> listInstalledFunctions();
	
	Promise<InstallResponseDTO> resetNode();

}
