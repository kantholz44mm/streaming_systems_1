package hob;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.ActiveMQMessageConsumer;

import org.apache.activemq.ActiveMQConnectionFactory;

public class TimestampConsumer {

	private String brokerUrl = "tcp://localhost:61616";

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageConsumer consumer;
	private transient Destination timestampTopic;

	private static final String topic = "TIMESTAMPS";

	
	public TimestampConsumer() throws JMSException {
		factory = new ActiveMQConnectionFactory(brokerUrl);
		connection = factory.createConnection("consumer", "password");
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		//		session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

		// use publish-subscribe
		timestampTopic = session.createTopic(topic);

		// use point-2-point
		timestampTopic = session.createQueue(topic); 
		consumer = session.createConsumer(timestampTopic);
	}

	public static void main(String[] args) throws JMSException {

		System.out.println("---> Consumer started.");

		TimestampConsumer consumer = new TimestampConsumer();
		consumer.consumeMessage();
	}

	private void consumeMessage() {
		try {
			consumer.setMessageListener(new Listener());
			System.out.println(consumer.getClass());
		} catch (JMSException e) {
			e.printStackTrace();
		}
	}

	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}
}
