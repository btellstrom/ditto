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

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSource}.
 */
public final class ImmutableSourceTest {

    private static final AuthorizationContext ctx = AuthorizationModelFactory.newAuthContext(
            AuthorizationModelFactory.newAuthSubject("eclipse"), AuthorizationModelFactory.newAuthSubject("ditto"));

    private static Map<String, String> mapping = new HashMap<>();
    static {
        mapping.put("correlation-id", "{{ header:message-id }}");
        mapping.put("thing-id", "{{ header:device_id }}");
        mapping.put("eclipse", "ditto");
    }

    private static final String AMQP_SOURCE1 = "amqp/source1";
    private static final Source SOURCE_WITH_AUTH_CONTEXT =
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(ctx)
                    .consumerCount(2)
                    .index(0)
                    .address(AMQP_SOURCE1)
                    .headerMapping(ConnectivityModelFactory.newHeaderMapping(mapping))
                    .build();

    private static final JsonObject SOURCE_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add(AMQP_SOURCE1).build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .set(Source.JsonFields.HEADER_MAPPING,
                    JsonFactory.newObjectBuilder().setAll(mapping.entrySet().stream()
                            .map(e -> JsonFactory.newField(JsonFactory.newKey(e.getKey()), JsonValue.of(e.getValue())))
                            .collect(Collectors.toList())).build())
            .build();

    private static final JsonObject SOURCE_JSON_WITH_AUTH_CONTEXT = SOURCE_JSON.toBuilder()
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSource.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSource.class, areImmutable(),
                provided(AuthorizationContext.class, Enforcement.class, HeaderMapping.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = SOURCE_WITH_AUTH_CONTEXT.toJson();
        assertThat(actual).isEqualTo(SOURCE_JSON_WITH_AUTH_CONTEXT);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Source actual = ImmutableSource.fromJson(SOURCE_JSON_WITH_AUTH_CONTEXT, 0);
        assertThat(actual).isEqualTo(SOURCE_WITH_AUTH_CONTEXT);
    }

}
