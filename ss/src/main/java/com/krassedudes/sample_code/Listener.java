package com.krassedudes.sample_code;


import javax.jms.TextMessage;

import org.joda.time.Instant;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

public class Listener implements MessageListener {

	public void onMessage(Message message) {
		try {
			String text = ((TextMessage)message).getText();
			System.out.println("Receiving: " + text + " at " + new Instant().toString());
		} catch (Exception e) {
			e.printStackTrace();
		}

		
		try {
			message.acknowledge();
		} catch (JMSException e) {
			e.printStackTrace();
		}
	}

}