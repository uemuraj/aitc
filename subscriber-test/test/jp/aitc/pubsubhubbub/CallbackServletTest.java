package jp.aitc.pubsubhubbub;

import static org.junit.Assert.assertEquals;

import java.io.*;

import org.junit.*;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

import com.meterware.httpunit.*;

public class CallbackServletTest {

	private static Server server;

	@BeforeClass
	public static void start() throws Exception {

		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath("/subscriber-test");
		webapp.setResourceBase(".");
		webapp.setTempDirectory(new File("output"));
		webapp.addServlet(CallbackServlet.class, "/callback");

		server = new Server(8888);
		server.addHandler(webapp);
		server.start();
	}

	@AfterClass
	public static void stop() throws Exception {
		server.stop();
		server.join();
	}

	private final String url = "http://localhost:8888/subscriber-test/callback";

	private final String challenge = "test-test-test";

	private final WebConversation conversation = new WebConversation();

	@Test
	public void hubVerifySubscribe() throws IOException {

		WebRequest request = new GetMethodWebRequest(url);
		request.setParameter("hub.mode", "subscribe");
		request.setParameter("hub.challenge", challenge);

		WebResponse response = conversation.getResource(request);
		assertEquals(200, response.getResponseCode());
		assertEquals(challenge, response.getText());
	}

	@Test
	public void hubVerifyUnsubscribe() throws IOException {

		WebRequest request = new GetMethodWebRequest(url);
		request.setParameter("hub.mode", "unsubscribe");
		request.setParameter("hub.challenge", challenge);

		WebResponse response = conversation.getResource(request);
		assertEquals(200, response.getResponseCode());
		assertEquals(challenge, response.getText());
	}

	@Test
	public void hubVerifyNoParameter1() throws IOException {

		WebRequest request = new GetMethodWebRequest(url);
		WebResponse response = conversation.getResource(request);
		assertEquals(404, response.getResponseCode());
	}

	@Test
	public void hubVerifyNoParameter2() throws IOException {

		WebRequest request = new GetMethodWebRequest(url);
		request.setParameter("hub.mode", "unsubscribe");

		WebResponse response = conversation.getResource(request);
		assertEquals(404, response.getResponseCode());
	}

	@Test
	public void hubVerifyInvalidParameter() throws IOException {

		WebRequest request = new GetMethodWebRequest(url);
		request.setParameter("hub.mode", "xyz");
		request.setParameter("hub.challenge", challenge);

		WebResponse response = conversation.getResource(request);
		assertEquals(404, response.getResponseCode());
	}

	private final String contentType = "application/atom+xml";

	private InputStream feed;

	@Before
	public void openContent() throws IOException {
		feed = getClass().getResourceAsStream("atom-feed.xml");
	}

	@After
	public void closeContent() throws IOException {
		feed.close();
	}

	@Test
	public void postContent() throws IOException {

		WebRequest request = new PostMethodWebRequest(url, feed, contentType);
		WebResponse response = conversation.getResource(request);
		assertEquals(200, response.getResponseCode());

		InputStream expected = getClass().getResourceAsStream("atom-link.xml");
		try {
			verifyContent(expected, new File("output", "atom-link.xml"));
		} finally {
			expected.close();
		}
	}

	private void verifyContent(InputStream expected, File file)
			throws IOException {

		InputStream actual = new FileInputStream(file);
		try {
			verifyContent(expected, actual);
		} finally {
			actual.close();
		}

	}

	private void verifyContent(InputStream expected, InputStream actual)
			throws IOException {

		int be, ba;
		do {
			be = expected.read();
			ba = actual.read();

			assertEquals(be, ba);

		} while (be != -1);
	}
}
