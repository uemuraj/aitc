package jp.aitc.pubsubhubbub;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

/**
 * PubSubHubbub サブスクライバの恐ろしく簡単な実装です。実用には足りませんが、サンプルとしてご活用ください。
 */
@WebServlet("/callback")
public class CallbackServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private File tempdir;

	private AtomicLong counter;

	private transient ExecutorService service;

	@Override
	public void init() throws ServletException {
		ServletContext context = getServletContext();
		tempdir = (File) context.getAttribute("javax.servlet.context.tempdir");
		counter = new AtomicLong();
		service = Executors.newSingleThreadExecutor();
	}

	@Override
	public void destroy() {

		try {
			service.shutdown();
			if (service.awaitTermination(60, TimeUnit.SECONDS)) {
				return;
			}

			service.shutdownNow();
			if (service.awaitTermination(60, TimeUnit.SECONDS)) {
				return;
			}

			log("service did not terminate.");

		} catch (InterruptedException ie) {

			service.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		// (0) 最低限、以下の２つのパラメータは処理する必要があります。処理そのものは hub.challenge の値をオウム返しするだけです。
		String mode = request.getParameter("hub.mode");
		String challenge = request.getParameter("hub.challenge");

		log("hub.mode = " + mode);
		log("hub.challenge = " + challenge);

		if (mode == null || challenge == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (!(mode.equals("subscribe") || mode.equals("unsubscribe"))) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		response.setContentType("application/x-www-form-urlencoded");

		PrintWriter writer = response.getWriter();
		try {
			writer.write(challenge);
		} finally {
			writer.close();
		}
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		
		long number = counter.incrementAndGet();

		// (1) ファイルに入力を全部そのまま保存します。保存されるのは Atom Feed データです。
		File rss = new File(tempdir, getRssFileName(number));
		log("rss = " + rss);

		ServletInputStream in = request.getInputStream();
		try {
			downloadToFile(in, rss);
		} finally {
			in.close();
		}

		// (2) 保存された Atom Feed を読み直して解析します。XPathを使ってリンク先のURLを抜き出します。
		InputStream xml = new FileInputStream(rss);
		try {
			XPathExpression exp = compile("//atom:entry/atom:link/@href");
			InputSource src = new InputSource(xml);
			NodeList list = (NodeList) exp
					.evaluate(src, XPathConstants.NODESET);

			// (3) リンク先の URL から気象庁防災情報XMLの本体をダウンロードします。複数あるかもしれません。
			for (int i = 0; i < list.getLength(); i++) {

				Node item = list.item(i);
				URL url = new URL(item.getNodeValue());
				log("url = " + url);

				File file = new File(tempdir, getUrlFileName(number, url));
				downloadToFile(url, file);

				// (4) 別スレッドで残りの処理をします。
				service.execute(new Command(file));
			}

		} catch (XPathException e) {
			throw new ServletException(e);
		} finally {
			xml.close();
		}
	}

	private String getRssFileName(long number) {

		// 通し番号と現在時刻を基にファイル名を作ります
		final String format = "%2$tY%2$tm%2$td%2$tH%2$tM%2$tS%2$tL-%1$09d.xml";

		return String.format(format, number, new Date());
	}

	private String getUrlFileName(long number, URL url) {

		// 通し番号と URL を基にファイル名を作ります
		final String format = "%1$09d-%2$s";

		File file = new File(url.getFile());

		return String.format(format, number, file.getName());
	}

	private void downloadToFile(InputStream in, File file) throws IOException {

		FileOutputStream out = new FileOutputStream(file);
		try {
			byte[] b = new byte[4096];
			int len;
			while ((len = in.read(b)) != -1) {
				out.write(b, 0, len);
			}

		} finally {
			out.close();
		}
	}

	private void downloadToFile(URL url, File file) throws IOException {

		InputStream in = url.openStream();
		try {
			downloadToFile(in, file);
		} finally {
			in.close();
		}
	}

	private XPathExpression compile(String expression) throws XPathException {

		// XPath の中で名前空間識別子を使うには、NamespaceContext の実装を指定してコンパイルする必要があります
		// ここでは、無名クラスとして恐ろしいほど手を抜いたものを実装しています

		XPath xpath = XPathFactory.newInstance().newXPath();

		xpath.setNamespaceContext(new NamespaceContext() {

			@Override
			public String getNamespaceURI(String prefix) {
				return "http://www.w3.org/2005/Atom";
			}

			@Override
			public String getPrefix(String namespaceURI) {
				return "atom";
			}

			@Override
			public Iterator<?> getPrefixes(String namespaceURI) {
				return Collections.singleton("atom").iterator();
			}
		});

		return xpath.compile(expression);
	}
	
	// (5) ここで後からゆっくり処理できます
	private class Command implements Runnable {
		
		private final File file;

		public Command(File file) {
			this.file = file;
		}

		@Override
		public void run() {
			log("file = " + file);
		}
	}
}
