package com.krassedudes.ss;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

public class Publisher {

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageProducer producer;
	private transient Destination message_queue;

	public Publisher(String url, String topic, String user, String pw) throws JMSException, Exception {

		factory = new ActiveMQConnectionFactory(url);
		connection = factory.createConnection(user, pw);
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		// use point-2-point
		message_queue = session.createQueue(topic); 

		producer = session.createProducer(message_queue);
		producer.setTimeToLive(1000);
	}

	public void publish(String payload) {
		try {
			Message message = session.createTextMessage(payload);
			producer.send(message);
		}
		catch(JMSException e)
		{
			System.err.println(e);
		}
	}

	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}

}