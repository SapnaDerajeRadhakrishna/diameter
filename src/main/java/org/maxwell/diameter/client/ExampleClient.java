/*
 * Ref: https://github.com/RestComm/jdiameter
 */

package org.maxwell.diameter.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.Configuration;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Message;
import org.jdiameter.api.MetaData;
import org.jdiameter.api.Network;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Request;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.Session;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.api.StackType;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;

import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.diameter.dictionary.AvpRepresentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleClient implements EventListener<Request, Answer> {

	private static final Logger log = LoggerFactory.getLogger("client");

	// configuration files
	private static final String configFile = "client/client-jdiameter-config.xml";
	private static final String dictionaryFile = "client/dictionary.xml";
	// our destination
	private static final String serverHost = "127.0.0.1";
	private static final String serverPort = "3868";

	@SuppressWarnings("unused")
	private static final String serverURI = "aaa://" + serverHost + ":" + serverPort;
	// our realm
	private static final String realmName = "exchange.example.org";
	// definition of codes, IDs
	private static final int commandCode = 318;
	private static final long vendorID = 10415;
	//private static final long applicationID = 16777251;
	private static final long applicationID = 16777265;
	private ApplicationId authAppId = ApplicationId.createByAuthAppId(applicationID);
	private static final int exchangeTypeCode = 263;
	private static final int exchangeDataCode = 999;
	// enum values for Exchange-Type AVP
	private static final int EXCHANGE_TYPE_INITIAL = 0;
	private static final int EXCHANGE_TYPE_INTERMEDIATE = 1;
	private static final int EXCHANGE_TYPE_TERMINATING = 2;
	// list of data we want to exchange.
	private static final String[] TO_SEND = new String[] { "I want to get 3 answers", "This is second message",
	        "Bye bye" };
	// Dictionary, for informational purposes.
	private AvpDictionary dictionary = AvpDictionary.INSTANCE;
	// stack and session factory
	private Stack stack;
	private SessionFactory factory;

	// ////////////////////////////////////////
	// Objects which will be used in action //
	// ////////////////////////////////////////
	private Session session; // session used as handle for communication
	private int toSendIndex = 0; // index in TO_SEND table
	private boolean finished = false; // boolean telling if we finished our interaction

	private void initStack() {
		log.info("Initializing Stack...");
		InputStream is = null;
		try {
			// Parse dictionary, it is used for user friendly info.
			dictionary.parseDictionary(this.getClass().getClassLoader().getResourceAsStream(dictionaryFile));
			log.info("AVP Dictionary successfully parsed.");

			this.stack = new StackImpl();
			// Parse stack configuration
			is = this.getClass().getClassLoader().getResourceAsStream(configFile);
			Configuration config = new XMLConfiguration(is);
			factory = stack.init(config);
			log.info("Stack Configuration successfully loaded.");
			// Print info about application
			Set<ApplicationId> appIds = stack.getMetaData().getLocalPeer().getCommonApplications();

			log.info("Diameter Stack  :: Supporting " + appIds.size() + " applications.");
			for (ApplicationId x : appIds) {
				log.info("Diameter Stack  :: Common :: " + x);
			}
			is.close();
			// Register network req listener, even though we wont receive requests
			// this has to be done to inform stack that we support application
			Network network = stack.unwrap(Network.class);
			network.addNetworkReqListener(new NetworkReqListener() {

				@Override
				public Answer processRequest(Request request) {
					// this wontbe called.
					return null;
				}
			}, this.authAppId); // passing our example app id.

		} catch (Exception e) {
			log.error("Exception;{}", e.getMessage());
			if (this.stack != null) {
				this.stack.destroy();
			}

			if (is != null) {
				try {
					is.close();
				} catch (IOException e1) {
					log.error("IOException;{}", e1.getMessage());
				}
			}
			return;
		}

		MetaData metaData = stack.getMetaData();
		// ignore for now.
		if (metaData.getStackType() != StackType.TYPE_SERVER || metaData.getMinorVersion() <= 0) {
			stack.destroy();
			log.error("Incorrect driver");
			return;
		}

		try {
			log.info("Starting stack");
			stack.start();
			log.info("Stack is running.");
		} catch (Exception e) {
			log.error("Exception;{}", e.getMessage());
			stack.destroy();
			return;
		}
		log.info("Stack initialization successfully completed.");
	}

	/**
	 * @return
	 */
	private boolean finished() {
		return this.finished;
	}

	/**
	 *
	 */
	private void start() {
		try {
			// wait for connection to peer
			try {
				TimeUnit.MILLISECONDS.sleep(5000);
			} catch (InterruptedException e) {
				log.error("Exception;{}", e.getMessage());
			}
			// do send
			this.session = this.factory
			        .getNewSession("BadCustomSessionId;YesWeCanPassId;" + System.currentTimeMillis());
			sendNextRequest(EXCHANGE_TYPE_INITIAL);
		} catch (InternalException | IllegalDiameterStateException | RouteException | OverloadException e) {
			log.error("Exception;{}", e.getMessage());
		}

	}

	@SuppressWarnings("unused")
	private void sendNextRequest(int enumType)
	        throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
		Request r = this.session.createRequest(commandCode, this.authAppId, realmName);
		// here we have all except our custom avps

		AvpSet requestAvps = r.getAvps();
		// code , value , vendor, mandatory,protected,isUnsigned32
		// (Enumerated)
		Avp exchangeType = requestAvps.addAvp(exchangeTypeCode, (long) enumType, vendorID, true, false, true); // value
		// is
		// set
		// on
		// creation
		// code , value , vendor, mandatory,protected, isOctetString
		Avp exchangeData = requestAvps.addAvp(exchangeDataCode, TO_SEND[toSendIndex++], vendorID, true, false, false); // value
		// is
		// set
		// on
		// creation
		// send
		requestAvps.addAvp(Avp.DESTINATION_HOST, serverHost, true, false, true);
		this.session.send(r, this);
		dumpMessage(r, true); // dump info on console
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.jdiameter.api.EventListener#receivedSuccessMessage(org.jdiameter .api.Message,
	 * org.jdiameter.api.Message)
	 */
	@Override
	public void receivedSuccessMessage(Request request, Answer answer) {
		dumpMessage(answer, false);
		if (answer.getCommandCode() != commandCode) {
			log.error("Received bad answer: {}" + answer.getCommandCode());
			return;
		}
		AvpSet answerAvpSet = answer.getAvps();

		Avp exchangeTypeAvp = answerAvpSet.getAvp(exchangeTypeCode, vendorID);
		Avp exchangeDataAvp = answerAvpSet.getAvp(exchangeDataCode, vendorID);
		Avp resultAvp = answer.getResultCode();

		try {
			// for bad formatted request.
			if (resultAvp.getUnsigned32() == 5005 || resultAvp.getUnsigned32() == 5004) {
				// missing || bad value of avp
				this.session.release();
				this.session = null;
				log.error("Something wrong happened at server side!");
				finished = true;
			}
			switch ((int) exchangeTypeAvp.getUnsigned32()) {
			case EXCHANGE_TYPE_INITIAL:
				String data = exchangeDataAvp.getUTF8String();
				if (data.equals(TO_SEND[toSendIndex - 1])) {
					sendNextRequest(EXCHANGE_TYPE_INTERMEDIATE);
				} else {
					log.error("Received wrong Exchange-Data: " + data);
				}
				break;
			case EXCHANGE_TYPE_INTERMEDIATE:
				data = exchangeDataAvp.getUTF8String();
				if (data.equals(TO_SEND[toSendIndex - 1])) {
					sendNextRequest(EXCHANGE_TYPE_TERMINATING);
				} else {
					log.error("Received wrong Exchange-Data: " + data);
				}
				break;
			case EXCHANGE_TYPE_TERMINATING:
				data = exchangeDataAvp.getUTF8String();
				if (data.equals(TO_SEND[toSendIndex - 1])) {
					finished = true;
					this.session.release();
					this.session = null;
				} else {
					log.error("Received wrong Exchange-Data: " + data);
				}
				break;
			default:
				log.error("Bad value of Exchange-Type avp: " + exchangeTypeAvp.getUnsigned32());
				break;
			}
		} catch (AvpDataException | InternalException | IllegalDiameterStateException | RouteException
		        | OverloadException e) {
			log.error("Exception;{}", e.getMessage());
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.jdiameter.api.EventListener#timeoutExpired(org.jdiameter.api. Message)
	 */
	@Override
	public void timeoutExpired(Request request) {

	}

	private void dumpMessage(Message message, boolean sending) {
		log.info((sending ? "Sending " : "Received ") + (message.isRequest() ? "Request: " : "Answer: ")
		        + message.getCommandCode() + "\nE2E:" + message.getEndToEndIdentifier() + "\nHBH:"
		        + message.getHopByHopIdentifier() + "\nAppID:" + message.getApplicationId());
		log.info("AVPS[" + message.getAvps().size() + "]: \n");
		try {
			printAvps(message.getAvps());
		} catch (AvpDataException e) {
			log.error("Exception;{}", e.getMessage());
		}
	}

	private void printAvps(AvpSet avpSet) throws AvpDataException {
		printAvpsAux(avpSet, 0);
	}

	/**
	 * Prints the AVPs present in an AvpSet with a specified 'tab' level
	 *
	 * @param avpSet
	 *            the AvpSet containing the AVPs to be printed
	 * @param level
	 *            an int representing the number of 'tabs' to make a pretty print
	 * @throws AvpDataException
	 */
	private void printAvpsAux(AvpSet avpSet, int level) throws AvpDataException {
		String prefix = "                      ".substring(0, level * 2);

		for (Avp avp : avpSet) {
			AvpRepresentation avpRep = AvpDictionary.INSTANCE.getAvp(avp.getCode(), avp.getVendorId());

			if (avpRep != null && avpRep.getType().equals("Grouped")) {
				log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\""
				        + avp.getVendorId() + "\">");
				printAvpsAux(avp.getGrouped(), level + 1);
				log.info(prefix + "</avp>");
			} else if (avpRep != null) {
				String value = "";

				if (avpRep.getType().equals("Integer32"))
					value = String.valueOf(avp.getInteger32());
				else if (avpRep.getType().equals("Integer64") || avpRep.getType().equals("Unsigned64"))
					value = String.valueOf(avp.getInteger64());
				else if (avpRep.getType().equals("Unsigned32"))
					value = String.valueOf(avp.getUnsigned32());
				else if (avpRep.getType().equals("Float32"))
					value = String.valueOf(avp.getFloat32());
				else
				    // value = avp.getOctetString();
				    value = new String(avp.getOctetString(), StandardCharsets.UTF_8);

				log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\""
				        + avp.getVendorId() + "\" value=\"" + value + "\" />");
			}
		}
	}

	public static void main(String[] args) {
		ExampleClient ec = new ExampleClient();
		ec.initStack();
		ec.start();

		while (!ec.finished()) {
			try {
				TimeUnit.MILLISECONDS.sleep(5000);
			} catch (InterruptedException e) {
				log.error("Exception;{}", e.getMessage());
			}
		}
	}

}