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
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;

public class RiverMarshallingEncoderTest extends AbstractMarshallingEncoderTest {

    @Override
    protected MarshallerFactory createMarshallerFactory() {
        return Marshalling.getProvidedMarshallerFactory(RIVER_FACTORY);
    }

    @Override
    protected MarshallingConfiguration createMarshallingConfig() {
        // Create a configuration
        final MarshallingConfiguration configuration = new MarshallingConfiguration();
        configuration.setVersion(3);
        return configuration;
    }

    @Override
    protected Buffer truncate(Buffer buf) {
        buf.readInt();
        return buf.split();
    }
}
