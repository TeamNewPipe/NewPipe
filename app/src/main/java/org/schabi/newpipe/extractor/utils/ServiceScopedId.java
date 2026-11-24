/*
 * SPDX-FileCopyrightText: PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Source: PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Shared filter contract retained for binary plugin compatibility.
 */

package org.schabi.newpipe.extractor.utils;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

/**
 * Channel/user ids are only unique inside one service (BiliBili and NicoNico both use plain
 * numbers), so block rules store them as {@code <serviceName>:<id>}, e.g.
 * {@code BiliBili:12345}. The service name is a stable part of the stored format.
 */
public final class ServiceScopedId {
    private static final char SEPARATOR = ':';

    private ServiceScopedId() {
    }

    /** @return the key for an id of the given service, or null if the id is empty/service unknown */
    public static String of(final int serviceId, final String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (final StreamingService service : ServiceList.all()) {
            if (service.getServiceId() == serviceId) {
                return service.getServiceInfo().getName() + SEPARATOR + id;
            }
        }
        return null;
    }

    /**
     * Validates user input and returns the canonical key (service name case fixed, whitespace
     * trimmed), or null if the input is not a {@code <serviceName>:<id>} key of a known service.
     */
    public static String normalize(final String input) {
        if (input == null) {
            return null;
        }
        final String trimmed = input.trim();
        final int index = trimmed.indexOf(SEPARATOR);
        if (index <= 0 || index == trimmed.length() - 1) {
            return null;
        }
        final String name = trimmed.substring(0, index).trim();
        final String id = trimmed.substring(index + 1).trim();
        if (id.isEmpty()) {
            return null;
        }
        for (final StreamingService service : ServiceList.all()) {
            final String serviceName = service.getServiceInfo().getName();
            if (serviceName.equalsIgnoreCase(name)) {
                return serviceName + SEPARATOR + id;
            }
        }
        return null;
    }
}
