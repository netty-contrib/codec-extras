/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.marshalling;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.TooLongFrameException;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;

import java.util.List;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SerialMarshallingDecoderTest extends AbstractMarshallingDecoderTest {

    @Override
    protected MarshallerFactory createMarshallerFactory() {
        return Marshalling.getProvidedMarshallerFactory(SERIAL_FACTORY);
    }

    @Override
    protected MarshallingConfiguration createMarshallingConfig() {
        // Create a configuration
        final MarshallingConfiguration configuration = new MarshallingConfiguration();
        configuration.setVersion(5);
        return configuration;
    }

    @Override
    protected Buffer input(byte[] input) {
        Buffer length = preferredAllocator().allocate(4);
        length.writeInt(input.length);
        return preferredAllocator().compose(List.of(length.send(), preferredAllocator().copyOf(input).send()));
    }

    @Override
    protected void onTooBigFrame(EmbeddedChannel ch, Buffer input) {
        try {
            ch.writeInbound(input);
            fail();
        } catch (CodecException e) {
            assertEquals(TooLongFrameException.class, e.getClass());
        }
    }
}
