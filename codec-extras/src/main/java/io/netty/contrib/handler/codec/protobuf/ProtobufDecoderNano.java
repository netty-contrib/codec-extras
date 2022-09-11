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
package io.netty.contrib.handler.codec.protobuf;

import com.google.protobuf.nano.MessageNano;
import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty5.handler.codec.MessageToMessageDecoder;

import static java.util.Objects.requireNonNull;

/**
 * Decodes a received {@link Buffer} into a
 * <a href="https://github.com/google/protobuf">Google Protocol Buffers</a>
 * {@link MessageNano}. Please note that this decoder must
 * be used with a proper {@link ByteToMessageDecoder} such as {@link LengthFieldBasedFrameDecoder}
 * if you are using a stream-based transport such as TCP/IP. A typical setup for TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder",
 *                  new {@link LengthFieldBasedFrameDecoder}(1048576, 0, 4, 0, 4));
 * pipeline.addLast("protobufDecoder",
 *                  new {@link ProtobufDecoderNano}(MyMessage.getDefaultInstance()));
 *
 * // Encoder
 * pipeline.addLast("frameEncoder", new {@link io.netty5.handler.codec.LengthFieldPrepender}(4));
 * pipeline.addLast("protobufEncoder", new {@link ProtobufEncoderNano}());
 * </pre>
 * and then you can use a {@code MyMessage} instead of a {@link Buffer}
 * as a message:
 * <pre>
 * void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     MyMessage req = (MyMessage) msg;
 *     MyMessage res = MyMessage.newBuilder().setText(
 *                               "Did you say '" + req.getText() + "'?").build();
 *     ch.write(res);
 * }
 * </pre>
 */
public class ProtobufDecoderNano extends MessageToMessageDecoder<Buffer> {
    private final Class<? extends MessageNano> clazz;

    /**
     * Creates a new instance.
     */
    public ProtobufDecoderNano(Class<? extends MessageNano> clazz) {
        this.clazz = requireNonNull(clazz, "You must provide a Class");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer msg) throws Exception {
        final int length = msg.readableBytes();
        final byte[] array = new byte[length];
        final int offset;
        msg.copyInto(msg.readerOffset(), array, 0, length);
        offset = 0;
        MessageNano prototype = clazz.getConstructor().newInstance();
        ctx.fireChannelRead(MessageNano.mergeFrom(prototype, array, offset, length));
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
