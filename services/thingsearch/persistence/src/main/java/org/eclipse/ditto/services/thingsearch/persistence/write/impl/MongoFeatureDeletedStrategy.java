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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;

/**
 * Strategy that creates {@link Bson} for {@link FeatureDeleted} events.
 */
public final class MongoFeatureDeletedStrategy extends MongoEventToPersistenceStrategy<FeatureDeleted> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final FeatureDeleted event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return Collections.singletonList(
                FeaturesUpdateFactory.createDeleteFeatureUpdate(event.getFeatureId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final FeatureDeleted event, final Enforcer policyEnforcer) {
        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(
                    PolicyUpdateFactory.createFeatureDeletion(event.getThingId(), event.getFeatureId()));
        }
        return Collections.emptyList();
    }
}
