package org.stayfool.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import org.stayfool.client.MqttClientOption;
import org.stayfool.client.event.EventKey;
import org.stayfool.client.event.EventManager;
import org.stayfool.client.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stayfool.client.message.*;

/**
 * Created by pactera on 2016/11/17.
 */
public class MqttClientHandler extends ChannelInboundHandlerAdapter {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        AbstractMessage message = (AbstractMessage) msg;
        try {
            switch (message.getMessageType()) {
                case MessageType.CONNACK:
                    processConnAck(ctx, (ConnAckMessage) message);
                    break;
                case MessageType.PUBLISH:
                    processPublish(ctx, (PublishMessage) message);
                    break;
                case MessageType.PUBACK:
                    processPubAck(ctx, (PubAckMessage) message);
                    break;
                case MessageType.PUBREC:
                    processPubRec(ctx, (PubRecMessage) message);
                    break;
                case MessageType.PUBREL:
                    processPubRel(ctx, (PubRelMessage) message);
                    break;
                case MessageType.PUBCOMP:
                    processPubComp(ctx, (PubCompMessage) message);
                    break;
                case MessageType.SUBACK:
                    processSubAck(ctx, (SubAckMessage) message);
                    break;
                case MessageType.UNSUBACK:
                    processUnsbAck(ctx, (UnsubAckMessage) message);
                    break;
                case MessageType.PINGRESP:
                    processPingResp(ctx, (PingRespMessage) message);
                    break;
                default:
                    throw new RuntimeException("Unacceptable Message Type");
            }
        } catch (Exception ex) {
            ctx.fireExceptionCaught(ex);
        }
    }

    private void processConnAck(ChannelHandlerContext ctx, ConnAckMessage message) {
        if (message.getReturnCode() == ConnAckMessage.CONNECTION_ACCEPTED) {
            log.debug("{} connect success", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
            EventManager.notify(new EventKey(EventType.CONNECT_SUCCESS, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
        } else {
            log.debug("{} connect failure", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
            EventManager.notify(new EventKey(EventType.CONNECT_FAILURE, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
        }
    }

    private void processPublish(ChannelHandlerContext ctx, PublishMessage message) {

        byte[] msg = new byte[message.getPayload().remaining()];
        message.getPayload().get(msg);
        log.debug("accept message : topic-{}; content-{}", message.getTopicName(), new String(msg, CharsetUtil.UTF_8));

        EventManager.notify(new EventKey(EventType.MESSAGE_ARRIVE, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);

        if (message.getQos().byteValue() > QOSType.MOST_ONE.byteValue()) {
            if (message.getQos() == QOSType.LEAST_ONE) {
                PubAckMessage pubAck = new PubAckMessage();
                pubAck.setMessageID(message.getMessageID());
                pubAck.setQos(message.getQos());
                ctx.channel().writeAndFlush(pubAck);
            } else {
                PubRecMessage pubRec = new PubRecMessage();
                pubRec.setMessageID(message.getMessageID());
                pubRec.setQos(message.getQos());
                ctx.channel().writeAndFlush(pubRec);
            }
        }

    }

    private void processPubAck(ChannelHandlerContext ctx, PubAckMessage message) {

        EventManager.notify(new EventKey(EventType.PUBLISH_SUCCESS, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
        log.debug("publish success : {}", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
    }

    private void processPubRec(ChannelHandlerContext ctx, PubRecMessage message) {
        if (message.getQos() == QOSType.EXACTLY_ONCE) {
            PubRelMessage pubRel = new PubRelMessage();
            pubRel.setMessageID(message.getMessageID());
            pubRel.setQos(message.getQos());
            ctx.channel().writeAndFlush(pubRel);
        }
    }

    private void processPubRel(ChannelHandlerContext ctx, PubRelMessage message) {
        if (message.getQos() == QOSType.EXACTLY_ONCE) {
            PubCompMessage pubComp = new PubCompMessage();
            pubComp.setMessageID(message.getMessageID());
            pubComp.setQos(message.getQos());
            ctx.channel().writeAndFlush(pubComp);
        }
    }

    private void processPubComp(ChannelHandlerContext ctx, PubCompMessage message) {
        EventManager.notify(new EventKey(EventType.PUBLISH_SUCCESS, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
        log.debug("publish success : {}", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
    }

    private void processSubAck(ChannelHandlerContext ctx, SubAckMessage message) {
        boolean success = true;
        if (message.types().isEmpty())
            success = false;
        if (success) {
            for (QOSType qosType : message.types()) {
                if (qosType.equals(QOSType.FAILURE)) {
                    success = false;
                    break;
                }
            }
        }
        if (success) {
            EventManager.notify(new EventKey(EventType.SUBSCRIBE_SUCCESS, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
            log.debug("subscribe success : {} ", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
        } else {
            EventManager.notify(new EventKey(EventType.SUBSCRIBE_FAILURE, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
            log.debug("subscribe failure : {} ", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
        }
    }

    private void processUnsbAck(ChannelHandlerContext ctx, UnsubAckMessage message) {
        EventManager.notify(new EventKey(EventType.UNSUBSCRIBE_SUCCESS, ctx.channel().attr(MqttClientOption.CLIENT_ID).get()), message);
        log.debug("unsubscribe success : {} ", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
    }

    private void processPingResp(ChannelHandlerContext ctx, PingRespMessage message) {
//        log.debug("unsubscribe success : {} ", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("bad thing happened : ", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
        log.error("lose connecttion : {}", ctx.channel().attr(MqttClientOption.CLIENT_ID).get());
    }

}