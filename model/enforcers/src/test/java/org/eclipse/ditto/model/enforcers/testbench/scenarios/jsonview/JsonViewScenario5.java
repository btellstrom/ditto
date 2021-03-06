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
package org.eclipse.ditto.model.enforcers.testbench.scenarios.jsonview;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class JsonViewScenario5 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario5() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/features/foo'. "
                        + "Subject has READ revoked on '/features/foo/properties/special'. " //
                        + "Is able to READ '/'. Can see in JsonView: '/features/foo/properties/ordinary'.", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED), //
                "/", //
                THING, //
                THING.toJson(JsonFieldSelector.newInstance("/features/foo/properties/ordinary")), //
                Stream.of(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED).toString())
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
