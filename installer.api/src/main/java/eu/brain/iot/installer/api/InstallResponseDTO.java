/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

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
