package com.krassedudes.ss;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.QueueSender;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.joda.time.Instant;
import org.springframework.jms.JmsException;

public class LidarPublisher {

	private static int total = 50000;
	private static final String topic = "LIDAR";

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageProducer producer;
	private transient Destination timestampTopic;
	

	private String brokerUrl = "tcp://localhost:61616";

	public LidarPublisher() throws JMSException, Exception {

		factory = new ActiveMQConnectionFactory(brokerUrl);
		connection = factory.createConnection("admin", "admin");
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		// use publish-subscribe
		timestampTopic = session.createTopic(topic); 

		// use point-2-point
		// timestampTopic = session.createQueue(topic); 

		producer = session.createProducer(timestampTopic);
		producer.setTimeToLive(1000);
				
	}

	public void publishLidarDataSet(LidarData data) {
		try {
			String payload = data.toJsonString();
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