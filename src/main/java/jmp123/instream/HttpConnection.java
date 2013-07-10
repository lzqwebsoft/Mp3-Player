package jmp123.instream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;

/**
 * HttpConnection是java.net.HttpURLConnection的“缩水版”。
 * 由于网络原因I/O发生阻塞时调用 {@link #close()} 能及时响应用户的中断。
 * 
 */
public class HttpConnection {
	private Socket socket;
	private InputStream instream;
	private HashMap<String, String> map;
	private String response;
	private String StatusLine;
	private int ResponseCode;
	private String ResponseMessage;
	private long ContentLength;

	/**
	 * 构造一个连接。
	 */
	public HttpConnection() {
		socket = new Socket();
		map = new HashMap<String, String>();
	}

	/**
	 * 打开 指定的Socket连接并解析HTTP响应头。
	 * 
	 * @param location
	 *            目标URL。
	 * @param referer
	 *            引用网址。
	 * @throws SocketException
	 *             如果底层协议出现错误，例如 TCP 错误。
	 * @throws IllegalArgumentException
	 *             如果端点为 null 或者此套接字不支持 SocketAddress 子类。
	 * @throws SocketTimeoutException
	 *             如果连接超时。
	 * @throws IOException
	 *             发生I/O错误。
	 */
	public void open(URL location, String referer) throws IOException {
		String host = location.getHost();
		if (referer == null)
			referer = "http://" + host + "/";
		String path = location.getPath();
		int port = location.getPort();
		if (port == -1)
			port = 80;

		socket.setSoTimeout(5000);
		//socket.setReceiveBufferSize(32 * 1024);
		socket.connect(new InetSocketAddress(host, port), 5000);
		PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

		// 构建HTTP请求头
		pw.println("GET " + path + " HTTP/1.1");
		pw.println("Host: " + host);
		pw.println("Referer: " + referer);
		pw.println("Accept: */*");
		pw.println("User-Agent: Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)");
		// pw.println("Range: bytes=" + startpos + "-");
		pw.println("Connection: Keep-Alive");
		pw.println();

		instream = socket.getInputStream();

		getResponse();
	}

	private void getResponse() throws IOException {
		// 获取HTTP响应头
		byte[] b = new byte[4096];
		int off, val, endcode = 0;
		for (off = 0; off < 4096 && endcode != 0x0d0a0d0a; off++) {
			if((val = instream.read()) == -1)
				break;
			b[off] = (byte) val;
			endcode <<= 8;
			endcode |= val;
			// System.out.printf("[%4d, 0x%08x] val = %d\n", off, int32, val);
		}
		//System.out.println("off = " + off);
		if (endcode != 0x0d0a0d0a) // 0x0d0a0d0a: "\r\n\r\n"
			throw new IOException("HTTP response header not found.");

		// 解析响应头
		response = new String(b, 0, off);
		String[] header = response.split("\r\n");
		if (header.length < 1)
			throw new IOException("Illegal response header.");

		StatusLine = header[0];
		parseStatusLine();

		String[] pair;
		for (String line : header) {
			pair = line.split(": ");
			if (pair.length == 2)
				map.put(pair[0], pair[1]);
		}

		try {
			ContentLength = Long.parseLong(map.get("Content-Length"));
		} catch (NumberFormatException e) {
			ContentLength = -1;
		}
	}

	// StatusLine = HTTP-Version SPACE Response-Code SPACE Reason-Phrase
	private void parseStatusLine() throws IOException {
		String[] s = StatusLine.split(" ");
		if(s.length < 3)
			throw new IOException("Illegal response status-line.");
		try {
			ResponseCode = Integer.parseInt(s[1]);
			ResponseMessage = s[2];
			for(int i = 3; i < s.length; i++)
				ResponseMessage += " " + s[i];
		} catch (NumberFormatException e) {
			ResponseCode = -1;
			throw new NumberFormatException("Illegal Response-Code: -1");
		}
	}

	/**
	 * 获取从此打开的连接读取的输入流。
	 * 
	 * @return 打开的连接读入的输入流。
	 */
	public InputStream getInputStream() {
		return instream;
	}

	/**
	 * 获取响应码。
	 * 
	 * @return 以整数形式返回响应码。
	 */
	public int getResponseCode() {
		return ResponseCode;
	}

	/**
	 * 获取 content-length 头字段的值。
	 * 
	 * @return 返回 content-length 头字段的值。
	 */
	public long getContentLength() {
		return ContentLength;
	}

	/**
	 * 获取HTTP响应的简短描述信息。
	 * 
	 * @return 响应的简短描述信息。
	 */
	public String getResponseMessage() {
		return ResponseMessage;
	}

	/**
	 * 返回指定的HTTP响应头字段的值。
	 * 
	 * @param key
	 *            头字段的名称。
	 * @return 指定的头字段的值，或者如果头中没有这样一个字段，则返回 null。
	 */
	public String getHeaderField(String key) {
		return map.get(key);
	}

	/**
	 * 在控制台打印HTTP响应头。
	 */
	public void printResponse() {
		if (response != null)
			System.out.println(response);
	}

	/**
	 * 关闭连接并清除（已经获取的）HTTP响应头。
	 * @throws IOException 关闭Socket时发生I/O错误。
	 */
	public void close() throws IOException {
		map.clear();
		socket.close();
	}
}
