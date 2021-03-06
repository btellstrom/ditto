/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Simple placeholder that currently only supports {@code {{ source:address }}} as a placeholder.
 * In the context of an incoming MQTT message the placeholder is resolved with the message topic.
 */
@Immutable
public final class ImmutableSourceAddressPlaceholder implements SourceAddressPlaceholder {

    /**
     * Singleton instance of the ImmutableSourceAddressPlaceholder.
     */
    static final ImmutableSourceAddressPlaceholder INSTANCE = new ImmutableSourceAddressPlaceholder();

    private static final String PREFIX = "source";
    private static final String VALUE = "address";

    private static final List<String> VALID_VALUES = Collections.unmodifiableList(
            Collections.singletonList(PREFIX + Placeholder.SEPARATOR + VALUE));

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return VALID_VALUES;
    }

    @Override
    public boolean supports(final String name) {
        return VALUE.equalsIgnoreCase(name);
    }

    @Override
    public Optional<String> apply(final String input, final String name) {
        return supports(name) ? Optional.of(input) : Optional.empty();
    }

    private ImmutableSourceAddressPlaceholder() {
    }
}
