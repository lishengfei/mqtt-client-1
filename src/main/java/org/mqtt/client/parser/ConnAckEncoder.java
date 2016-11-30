/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.mqtt.client.parser;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.mqtt.client.message.ConnAckMessage;
import org.mqtt.client.message.MessageType;

/**
 * @author andrea
 */
class ConnAckEncoder extends DemuxEncoder<ConnAckMessage> {

    @Override
    protected void encode(ChannelHandlerContext chc, ConnAckMessage message, ByteBuf out) {
        out.writeByte(MessageType.CONNACK << 4);
        out.writeBytes(Utils.encodeRemainingLength(2));
        out.writeByte(message.isSessionPresent() ? 0x01 : 0x00);
        out.writeByte(message.getReturnCode());
    }

}
