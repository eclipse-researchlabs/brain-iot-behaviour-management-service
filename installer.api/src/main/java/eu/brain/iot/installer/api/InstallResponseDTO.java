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

/**
 * An event sent by the installer to notify the requester about the install status.
 */
public class InstallResponseDTO {
    public enum ResponseCode {
        SUCCESS,
        BAD_REQUEST,
        FAIL;
    }

    public ResponseCode code;

    /**
     * response messages
     */
    public List<String> messages;

}
