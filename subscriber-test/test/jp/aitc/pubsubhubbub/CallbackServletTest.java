package jp.aitc.pubsubhubbub;

import static org.junit.Assert.*;

import java.io.*;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.*;

import com.meterware.httpunit.*;

public class CallbackServletTest {

	private static Server server;
	
	private static File tempdir = new File("output");

	@BeforeClass
	public static void start() throws Exception {
		server = new Server(8888);
		server.setHandler(webapps());
		server.start();
	}

	@AfterClass
	public static void stop() throws Exception {
		server.stop();
		server.join();
	}

	private static Handler webapps() {

		remove(tempdir);

		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath("/subscriber-test");
		webapp.setResourceBase(".");
		webapp.setTempDirectory(tempdir);
		webapp.addServlet(CallbackServlet.class, "/callback");

		ContextHandlerCollection contexts = new ContextHandlerCollection();
		contexts.addHandler(webapp);
		return contexts;
	}

	private static void remove(File... files) {

		for (File file : files) {

			if (file.isDirectory()) {
				remove(file.listFiles());
			}

			if (file.exists()) {
				file.delete();
			}
		}
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
			verifyContent(expected, getOutputs(new File("output"), "atom-link"));
		} finally {
			expected.close();
		}
	}

	private File[] getOutputs(File dir, final String prefix) {

		return dir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(prefix);
			}
		});
	}

	private void verifyContent(InputStream expected, File... files)
			throws IOException {

		for (File file : files) {

			InputStream actual = new FileInputStream(file);
			try {
				verifyContent(expected, actual);
			} finally {
				actual.close();
			}
		}
		
		assertTrue(files.length > 0);
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
