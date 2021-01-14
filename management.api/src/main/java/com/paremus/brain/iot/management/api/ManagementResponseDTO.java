/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package com.paremus.brain.iot.management.api;

/**
 * An response event sent by the behaviour management service
 */
public class ManagementResponseDTO extends TargettedManagementDTO {
    public enum ResponseCode {
        /**
         * bid to install requirements
         */
        BID,

        /**
         * requirement is already installed
         */
        ALREADY_INSTALLED,

        /**
         * requirements installed successfully
         */
        INSTALL_OK,

        /**
         * failed to bid or install requirements
         */
        FAIL;
    }

    public ResponseCode code;

    public Integer bid;
    
    public String message;

}
