package hob;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.QueueSender;
import javax.jms.Session;
import javax.jms.TopicPublisher;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.joda.time.Instant;

public class TimestampPublisher {

	private static int total = 50000;
	private static final String topic = "TIMESTAMPS";

	private transient ConnectionFactory factory;
	private transient Connection connection;
	private transient Session session;
	private transient MessageProducer producer;
	private transient Destination timestampTopic;
	

	private String brokerUrl = "tcp://localhost:61616";

	public TimestampPublisher() throws JMSException, Exception {

		factory = new ActiveMQConnectionFactory(brokerUrl);
		connection = factory.createConnection("publisher", "password");
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		// use publish-subscribe
		timestampTopic = session.createTopic(topic); 

		// use point-2-point
		timestampTopic = session.createQueue(topic); 

		producer = session.createProducer(timestampTopic);
		producer.setTimeToLive(1000);
				
	}

	public static void main(String[] args) throws JMSException, Exception {
		TimestampPublisher publisher = new TimestampPublisher();
		int i = 0;
		while (i++ < total) {
			publisher.sendMessage();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		publisher.close();
	}

	protected void sendMessage() throws JMSException {
		// bestimme aktuelle Zeit
		String timestamp = new Instant().toString();
		// erzeuge und versende die Nachricht
		Message message = session.createTextMessage(timestamp);
		System.out.println("Sending: " + " " + timestamp);
		producer.send(message);
	}


	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}

}