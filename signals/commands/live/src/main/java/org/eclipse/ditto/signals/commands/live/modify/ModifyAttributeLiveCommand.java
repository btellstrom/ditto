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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * {@link ModifyAttribute} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link ModifyAttributeLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface ModifyAttributeLiveCommand
        extends LiveCommand<ModifyAttributeLiveCommand, ModifyAttributeLiveCommandAnswerBuilder>,
        ThingModifyCommand<ModifyAttributeLiveCommand> {

    /**
     * Returns the JSON pointer of the attribute to modify.
     *
     * @return the JSON pointer.
     * @see ModifyAttribute#getAttributePointer()
     */
    @Nonnull
    JsonPointer getAttributePointer();

    /**
     * Returns the value of the attribute to modify.
     *
     * @return the value.
     * @see ModifyAttribute#getAttributeValue()
     */
    @Nonnull
    JsonValue getAttributeValue();

}
