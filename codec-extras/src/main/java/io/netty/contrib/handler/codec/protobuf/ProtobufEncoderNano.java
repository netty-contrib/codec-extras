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

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import io.netty5.buffer.ByteBuf;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty5.handler.codec.LengthFieldPrepender;
import io.netty5.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Encodes the requested <a href="https://github.com/google/protobuf">Google
 * Protocol Buffers</a> {@link MessageNano} into a
 * {@link ByteBuf}. A typical setup for TCP/IP would be:
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
 * pipeline.addLast("frameEncoder", new {@link LengthFieldPrepender}(4));
 * pipeline.addLast("protobufEncoder", new {@link ProtobufEncoderNano}());
 * </pre>
 * and then you can use a {@code MyMessage} instead of a {@link ByteBuf}
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
@ChannelHandler.Sharable
public class ProtobufEncoderNano extends MessageToMessageEncoder<MessageNano> {
    @Override
    protected void encode(
            ChannelHandlerContext ctx, MessageNano msg, List<Object> out) throws Exception {
        final int size = msg.getSerializedSize();
        final ByteBuf buffer = ctx.alloc().heapBuffer(size, size);
        final byte[] array = buffer.array();
        CodedOutputByteBufferNano cobbn = CodedOutputByteBufferNano.newInstance(array,
                buffer.arrayOffset(), buffer.capacity());
        msg.writeTo(cobbn);
        buffer.writerIndex(size);
        out.add(buffer);
    }
}
