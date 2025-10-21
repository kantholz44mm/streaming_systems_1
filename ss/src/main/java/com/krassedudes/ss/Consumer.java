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

import java.util.function.Function;

import org.apache.activemq.ActiveMQConnectionFactory;

public class Consumer implements MessageListener {

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageConsumer consumer;
	private transient Destination message_queue_raw;
	private transient java.util.function.Consumer<String> callback;
	
	public Consumer(String url, String topic, String user, String pw, java.util.function.Consumer<String> callback) throws JMSException {
		this.callback = callback;
		factory = new ActiveMQConnectionFactory(url);
		connection = factory.createConnection(user, pw);
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		//		session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

		// use publish-subscribe
		// timestampTopic = session.createTopic(topic);

		// use point-2-point
		message_queue_raw = session.createQueue(topic); 
		consumer = session.createConsumer(message_queue_raw);
		consumer.setMessageListener(this);
	}

	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}

	@Override
	public void onMessage(Message msg) {
		
		try {
			TextMessage text_message = (TextMessage)msg;
			String payload = text_message.getText();
			callback.accept(payload);
		}
		catch(JMSException e)
		{
			// ignore all other messages.
		}
	}
}
