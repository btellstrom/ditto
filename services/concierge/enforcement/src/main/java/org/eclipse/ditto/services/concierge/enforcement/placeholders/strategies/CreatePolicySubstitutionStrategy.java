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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;

/**
 * Handles substitution for {@link org.eclipse.ditto.model.policies.SubjectId}
 * inside a {@link CreatePolicy} command.
 */
final class CreatePolicySubstitutionStrategy extends AbstractTypedSubstitutionStrategy<CreatePolicy> {

    CreatePolicySubstitutionStrategy() {
        super(CreatePolicy.class);
    }

    @Override
    public WithDittoHeaders apply(final CreatePolicy createPolicy,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(createPolicy);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = createPolicy.getDittoHeaders();
        final Policy existingPolicy = createPolicy.getPolicy();
        final Policy substitutedPolicy =
                substitutePolicy(existingPolicy, substitutionAlgorithm, dittoHeaders);

        if (existingPolicy.equals(substitutedPolicy)) {
            return createPolicy;
        } else {
            return CreatePolicy.of(substitutedPolicy, dittoHeaders);
        }
    }

}
