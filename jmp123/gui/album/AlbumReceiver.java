package jmp123.gui.album;

import javax.swing.ImageIcon;

/**
 * 接收唱片集搜索结果。该该接口的方法由接收者实现，被搜索者调用。
 * 
 */
public interface AlbumReceiver {
	/**
	 * 接收者准备就绪。
	 * 
	 * @param artist
	 *            艺术家。
	 */
	public void ready(String artist);

	/**
	 * 向接收者发送一个由 imgIcon 指定的搜索结果。
	 * 
	 * @param imgIcon
	 *            搜索到的一个唱片集图片对象。该对象是一个已经被初始化的接收者可以直接使用的对象。
	 */
	public void addImageIcon(ImageIcon imgIcon);

	/**
	 * 搜索者结束搜索时调用此方法以通知接收者作相应处理。
	 * 
	 * @param interrupted
	 *            是否被用户终止。搜索过程被用户终止true，否则false。
	 */
	public void completed(boolean interrupted);
}
