package com.arubanetworks.aledemonstrator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


public class ZMQSubscriber extends Thread {
	private String TAG = "ZMQSubscriberThread";
	private final Handler uiThreadHandler;
	private String[] zmqFilter;
	final ZContext zContext = new ZContext();
	ZMQ.Socket socket;

	public ZMQSubscriber(Handler uiThreadHandler, String[] zmqFilter) {
		this.uiThreadHandler = uiThreadHandler;
		this.zmqFilter = zmqFilter;
	}

	private UncaughtExceptionHandler exHandler = new Thread.UncaughtExceptionHandler() {
		public void uncaughtException(Thread thread, Throwable throwable) {
			Log.e(TAG, "hit the uncaught exception "+throwable.toString());
			interrupt();
		}
	};

	public void run() {
		try{
			setDefaultUncaughtExceptionHandler(exHandler);
			String target = "all devices";
			if(zmqFilter[0].contains("location/")) { target = zmqFilter[0].substring(zmqFilter[0].indexOf("/")+1); }
			String progress = " ";
			if(!testReachabilityOfServer()) { progress = MainActivity.aleHost+" unreachable"; }
			else { progress = MainActivity.aleHost+" opened socket for "+target; }
			Log.v(TAG, "ZMQ server "+MainActivity.aleHost+"  "+progress);
			sendMessage(MainActivity.ZMQ_PROGRESS_MESSAGE, progress.getBytes("UTF-8"));

			String connectString = "tcp://"+MainActivity.aleHost+":7779";
			socket = zContext.createSocket(ZMQ.SUB);

			socket.connect(connectString);

			String filterString = "";
			for(int i=0; i<zmqFilter.length; i++){
				socket.subscribe(zmqFilter[i].getBytes("UTF-8"));
				filterString = filterString + zmqFilter[i]+" ";
			}

			byte[] msg = null;

			while(!Thread.currentThread().isInterrupted()) {
				String topic = new String(socket.recv(0));
				msg = socket.recv(0);
				while(socket.hasReceiveMore()){
					Log.v(TAG, "multi-part zmq message ");
					byte[] moreBytes = socket.recv(0);
					byteConcat(msg, moreBytes);
				}
				sendMessage(topic, msg);
			}

			socket.close();

		} catch (ZMQException e)    {
			Log.e(TAG, "ZMQException "+e);
			sendMessage(MainActivity.ZMQ_PROGRESS_MESSAGE, e.toString().getBytes());
		} catch (Exception e) {
			Log.e(TAG, "Exception "+e);
			sendMessage(MainActivity.ZMQ_PROGRESS_MESSAGE, e.toString().getBytes());
		}
	}

	private byte[] byteConcat(byte[] a, byte[] b){
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a,  0,  result,  0,  a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	private void sendMessage(String key, byte[] content){
		Bundle bundle = new Bundle();
		bundle.putByteArray(key, content);
		Message message = Message.obtain(uiThreadHandler);
		message.setData(bundle);
		uiThreadHandler.sendMessage(message);
	}

	private boolean testReachabilityOfServer(){
		boolean success = false;
		try {
			SocketAddress sockaddr = new InetSocketAddress(MainActivity.aleHost, 7779);
			Log.i(TAG, "testing reachability to "+sockaddr.toString());
			java.net.Socket sock = new java.net.Socket();
			int timeoutMs = 2000;   // 2 seconds
			sock.connect(sockaddr, timeoutMs);
			success = true;
			Log.i(TAG, "successful reachability to "+sockaddr.toString());
		}catch(Exception e){
			Log.e(TAG, MainActivity.aleHost+" address not reachable after 2sec "+e);
		}
		return success;
	}

	@Override
	public void interrupt(){
		Log.w(TAG, "interrupting ZMQ thread");
		super.interrupt();
	}


}