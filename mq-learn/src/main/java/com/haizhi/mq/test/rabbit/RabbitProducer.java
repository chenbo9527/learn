package com.haizhi.mq.test.rabbit;

import com.haizhi.mq.base.Callback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Created by chenbo on 15/11/20.
 */
public class RabbitProducer {
    public static final String TYPE_TOPIC = "topic";

    private static final String DEFAULT_HOST = "localhost";
    private static final Logger logger = LoggerFactory.getLogger(RabbitProducer.class);

    private String host;
    private List<Callback> callbackList = new ArrayList<Callback>();

    private ConnectionFactory factory = new ConnectionFactory();
    private Connection connection;
    private Channel channel;

    public RabbitProducer() {
        this(DEFAULT_HOST);
    }

    public RabbitProducer(String host) {
        this.host = (host != null && host.length() > 0) ? host : DEFAULT_HOST;

        //让每个producer占用不同的connection，以免关闭本连接影响其他producer
        factory.setHost(this.host);
        try {
            logger.info("RabbitMqProducer start...");
            connection = factory.newConnection();
            channel = connection.createChannel();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    public void addCallback(Callback callback) {
        callbackList.add(callback);
    }

    public void send(RabbitMessage message) {
        try {
            if(message.getQueueName() != null){
//                channel.queueDeclare("topic_queue",true,false,false,null);
                channel.queueDeclare(message.getQueueName(), true, false, false, null);
            }
            channel.exchangeDeclare(message.getExchange(), TYPE_TOPIC);
            channel.basicPublish(message.getExchange(), message.getRoutingKey(), null, message.getBody());

            logger.info("Message sent, RabbitMessage: "+message);
            processOnSuccess(message);
        } catch (IOException e) {
            //交给具体的使用者去实现处理处理逻辑
            processOnException(message, e);
            logger.error("消息发送失败, RabbitMessage: {}", message, e);
        }
    }

    private void processOnSuccess(RabbitMessage message) {
        //默认没有执行动作
        if (callbackList != null && callbackList.size() > 0) {
            for (Callback callback : callbackList) {
                callback.onSuccess(message);
            }
        }
    }

    private void processOnException(RabbitMessage message, Throwable e) {
        if (callbackList != null && callbackList.size() > 0) {
            for (Callback callback : callbackList) {
                callback.onException(message, e);
            }
        } else {
            logger.warn("callbackList 为空, processOnException 无法执行");
        }
    }

    public void closeConnection(){
        if(connection != null){
            try {
                logger.info("Producer quit. Connection: {"+connection+"}");
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
