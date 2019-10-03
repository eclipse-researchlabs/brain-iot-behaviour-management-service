package com.paremus.brain.iot.management.impl;

import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;
import com.paremus.brain.iot.management.api.ManagementResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO;

import java.util.Map;

public interface ConsumerNotify {
    void notify(InstallResponseDTO event);

    void notify(ManagementResponseDTO event);

    void notify(ManagementInstallRequestDTO event);

    void notifyLastResort(String eventType, Map<String, ?> properties);
}
