package com.krassedudes.ss;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

public class LidarConsumer implements MessageListener {

	private String brokerUrl = "tcp://localhost:61616";

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageConsumer consumer;
	private transient Destination lidartopic;

	private static final String topic = "LIDAR";

	
	public LidarConsumer() throws JMSException {
		factory = new ActiveMQConnectionFactory(brokerUrl);
		connection = factory.createConnection("admin", "admin");
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		//		session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

		// use publish-subscribe
		// timestampTopic = session.createTopic(topic);

		// use point-2-point
		lidartopic = session.createQueue(topic); 
		consumer = session.createConsumer(lidartopic);
		consumer.setMessageListener(this);
	}

	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}

	@Override
	public void onMessage(Message msg) {
		// do stuff.
		try {
			TextMessage text_message = (TextMessage)msg;
			String payload = text_message.getText();
			LidarData data = LidarData.fromJsonString(payload);

			System.out.println(data.toString());
		}
		catch(JMSException e)
		{
			// ignore.
		}
	}
}
