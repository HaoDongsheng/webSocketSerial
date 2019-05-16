package org.hds.WebSocket;

import org.hds.GJ_coding.GJ_CommandWordEnum;
import org.hds.GJ_coding.GJ_LedDecodingCls;
import org.hds.GJ_coding.GJ_codingCls;
import org.hds.RTXTX.RXTXtest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import javassist.expr.NewArray;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
 
/**
 * Created by wzj on 2018/3/14.
 */
@ServerEndpoint(value = "/websocket")
@Component
public class MyWebSocket
{
	final Logger logger=LoggerFactory.getLogger(this.getClass());
	SerialPort serialPort = null;
	int commandSN =-1;
    /**
     * 在线人数
     */
    public static int onlineNumber = 0;
 
    /**
     * 所有的对象
     */
    public static List<MyWebSocket> webSockets = new CopyOnWriteArrayList<MyWebSocket>();
 
    /**
     * 会话
     */
    private Session session;
 
    /**
     * 建立连接
     *
     * @param session
     */
    @OnOpen
    public void onOpen(Session session)
    {
        onlineNumber++;
        webSockets.add(this);
 
        this.session = session;
 
        System.out.println("有新连接加入！ 当前在线人数" + onlineNumber);
    }
 
    /**
     * 连接关闭
     */
    @OnClose
    public void onClose()
    {
        onlineNumber--;
        webSockets.remove(this);
        if(serialPort!=null)
		{RXTXtest.closeSerialPort(serialPort);}
        System.out.println("有连接关闭！ 当前在线人数" + onlineNumber);
    }
 
    /**
     * 收到客户端的消息
     *
     * @param message 消息
     * @param session 会话
     */
    @OnMessage
    public void onMessage(String message, Session session)
    {
    	 try
         {
    		logger.info("===来自客户端消息:" + message +"===");
	        JSONObject jsonObject=JSONObject.parseObject(message);
	        String command = jsonObject.getString("command");
	        switch (command) {
			case "getComlist":{
				List<String> comlist = RXTXtest.getSystemPort();
							
				JSONObject jsonSendJsonObject=new JSONObject();
				jsonSendJsonObject.put("command", "returnComlist");
				jsonSendJsonObject.put("commandSN", jsonObject.getIntValue("commandSN"));
				jsonSendJsonObject.put("comlist", comlist.toString());
				byte[] bytes = jsonSendJsonObject.toJSONString().getBytes();
				sendMessage(new String(bytes));					
			};break;
			case "openSerialPort":{
				String serialPortName =jsonObject.getString("serialPortName");
				int baudRate =jsonObject.getIntValue("baudRate");
				if(serialPort!=null)
				{RXTXtest.closeSerialPort(serialPort);}
				serialPort = RXTXtest.openSerialPort(serialPortName, baudRate);
				JSONObject jsonSendJsonObject=new JSONObject();
				jsonSendJsonObject.put("command", "returnopenSerialPort");
				jsonSendJsonObject.put("commandSN", jsonObject.getIntValue("commandSN"));
				if(serialPort!=null)
				{jsonSendJsonObject.put("SerialPort", true);}
				else {
					jsonSendJsonObject.put("SerialPort", false);
				}
								
				byte[] bytes = jsonSendJsonObject.toJSONString().getBytes();
				sendMessage(new String(bytes));					
				
				RXTXtest.setListenerToSerialPort(serialPort, new SerialPortEventListener() {
					
					@Override
					public void serialEvent(SerialPortEvent arg0) {
						try {
							if(arg0.getEventType() == SerialPortEvent.DATA_AVAILABLE) {//数据通知
								Thread.sleep(200);
								byte[] returnbytes = RXTXtest.readData(serialPort);
								
								GJ_codingCls codingCls=new GJ_codingCls(1024);
						    	GJ_LedDecodingCls decodingCls = codingCls.GJ_Decoding(returnbytes);
						    	    
								GJ_CommandWordEnum commandWordEnum = decodingCls.getMcommandword();
								switch (commandWordEnum) {	
								case dele:
								case aadv:
								case alst:
								{
									logger.info("===接收串口数据:"+ byteToHex(returnbytes) +"===");
									
									JSONObject jsonSendJsonObject=new JSONObject();
									jsonSendJsonObject.put("command", "returnSerialPortData");
									jsonSendJsonObject.put("commandSN", commandSN);
									jsonSendJsonObject.put("returnData", byteToHex(returnbytes));
									byte[] bytes = jsonSendJsonObject.toJSONString().getBytes();
									sendMessage(new String(bytes));	
								};break;
								}									
							}	
						} catch (Exception e) {
							// TODO: handle exception
						}
						
					}
				});
			};break;		
			case "sendSerialData":{
				if(serialPort!=null)
				{
					String sendData =jsonObject.getString("sendData");
					commandSN = jsonObject.getIntValue("commandSN");
					logger.info("===发送串口数据:"+ sendData +"===");
					byte[] bytes = hexToByte(sendData);
			        RXTXtest.sendData(serialPort, bytes);
				}
			};break;
			case "closeSerialPort":{
				if(serialPort!=null)
				{RXTXtest.closeSerialPort(serialPort);}
			};break;
			default:
				break;
			}
         }
         catch (Exception e)
         {
             e.printStackTrace();
         }
    }
    /**
     * hex转byte数组
     * @param hex
     * @return
     */
    public static byte[] hexToByte(String hex){
        int m = 0, n = 0;
        hex = hex.replace(" ", "").trim();
        int byteLen = hex.length() / 2; // 每两个字符描述一个字节
        byte[] ret = new byte[byteLen];
        for (int i = 0; i < byteLen; i++) {
            m = i * 2 + 1;
            n = m + 1;
            int intVal = Integer.decode("0x" + hex.substring(i * 2, m) + hex.substring(m, n));
            ret[i] = Byte.valueOf((byte)intVal);
        }
        return ret;
    }
    
    /**
     * byte数组转hex
     * @param bytes
     * @return
     */
    public static String byteToHex(byte[] bytes){
        String strHex = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < bytes.length; n++) {
            strHex = Integer.toHexString(bytes[n] & 0xFF);
            sb.append((strHex.length() == 1) ? "0" + strHex : strHex); // 每个字节由两个字符表示，位数不够，高位补0
        }
        return sb.toString().trim();
    }
 
    /**
     *	 发送消息
     *
     * @param message 消息
     */
    public void sendMessage(String message)
    {
        try
        {
            session.getBasicRemote().sendText(message);
            logger.info("===来自服务端端消息:" + message +"===");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
