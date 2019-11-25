/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

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
