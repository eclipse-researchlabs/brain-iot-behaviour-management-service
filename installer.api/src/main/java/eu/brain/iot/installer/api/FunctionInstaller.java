/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
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
