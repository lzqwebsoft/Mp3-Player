package jmp123.gui.album;

import java.net.URL;
import java.net.URLClassLoader;

public class AlbumThread extends Thread {
	private static Album album;
	private AlbumReceiver receiver;
	private String artist;
	private volatile boolean interrupted;

	public AlbumThread(AlbumReceiver receiver, String artist) {
		this.receiver = receiver;
		this.artist = artist;
	}

	@Override
	public void interrupt() {
		interrupted = true;

		synchronized (this) {
			if (album != null)
				album.interrupt();
		}

		super.interrupt();
	}

	@Override
	public void run() {
		interrupted = false;
		if (album == null)
			init();

		if (album != null)
			album.search(receiver, artist);

		synchronized (this) {
			receiver.completed(interrupted);
		}
	}

	/**
	 * 加载远程jar包初始化 album 对象。
	 * <p>
	 * 网络搜索图片的方法不公开源代码。实现图片搜索功能的远程jar包随时可能被作者删除。
	 * <p>
	 * 如果你有更好的唱片集搜索方法：
	 * <p>(1)写实现Album接口方法的AlbumImpl类；
	 * <p>(2)替换init()方法内所有语句为album=new AlbumImpl();
	 */
	private synchronized void init() {
		//System.out.println("[AlbumThread.init] load .jar");
		try {
			URL[] urls = { new URL("jar:http://jmp123.sf.net/jar/album.jar!/") };
			album = (Album) Class.forName("jmp123.gui.album.AlbumImpl", true,
					new URLClassLoader(urls)).newInstance();
		} catch (Exception e) {
			System.out.println("AlbumThread.init(): " + e.toString());
		}
		//album = new AlbumImpl();
	}

}
