/**
 * 图片搜索接口。
 */
package jmp123.gui.album;


/**
 * 从网络搜索唱片集。
 */
public interface Album {
	/**
	 * 搜索由 artist 指定的艺术家唱片集。在搜索过程中，如果查找到图片：
	 * <ul><li>查找到首张图片时调用 receiver.ready(String) 让接收者准备接收图片。</li>
	 * <li>调用 receiver.addImageIcon(ImageIcon) 让接收者接收一张图片。</li>
	 * <li>搜索结束时调用 receiver.completed(boolean) 通知接收者。</li></ul>
	 * 
	 * @param receiver
	 *            接收此搜索结果的对象。
	 * @param artist
	 *            艺术家。
	 * @see {@link AlbumReceiver#ready(String)}
	 * @see {@link AlbumReceiver#addImageIcon(javax.swing.ImageIcon)}
	 */
	public void search(AlbumReceiver receiver, String artist);

	/**
	 * 中断搜索。
	 */
	public void interrupt();

	/**
	 * 搜索过程中是否被用户终止。
	 * 
	 * @return 搜索过程中是否被用户终止返回true，否则返回false。
	 */
	public boolean isInterrupted();

}
