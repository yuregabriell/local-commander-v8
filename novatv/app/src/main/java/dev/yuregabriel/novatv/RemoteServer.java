package dev.yuregabriel.novatv;

import android.os.Handler;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.Enumeration;

public final class RemoteServer {
    public interface Listener {
        void onMove(float dx, float dy);
        void onClick();
        void onBack();
        void onNavigate(String key);
    }
    public static final int PORT = 42377;
    public final String pin;
    private volatile boolean running;
    private ServerSocket server;
    private Socket client;
    private final Handler ui;
    private final Listener listener;
    public RemoteServer(Handler ui,Listener listener) {
        this.ui=ui;this.listener=listener;
        pin=String.format(java.util.Locale.US,"%06d",new SecureRandom().nextInt(1000000));
    }
    public void start() {
        if(running)return;
        running=true;
        new Thread(()->{
            try(ServerSocket ss=new ServerSocket(PORT)){
                server=ss;
                ss.setSoTimeout(1200);
                while(running){
                    try{
                        Socket s=ss.accept();
                        if(client!=null)try{client.close();}catch(Exception ignored){}
                        client=s;
                        s.setSoTimeout(15000);
                        handle(s);
                    } catch(SocketTimeoutException ignored) {}
                    catch(Exception ignored){}
                }
            }catch(IOException ignored){}
        },"nova-local-remote").start();
    }
    public void stop() {
        running=false;
        if(client!=null)try{client.close();}catch(IOException ignored){}
        if(server!=null)try{server.close();}catch(IOException ignored){}
    }
    private void handle(Socket s) throws IOException {
        try(Socket socket=s;
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF-8"))) {
            String first=reader.readLine();
            if(!("AUTH "+pin).equals(first)){
                writer.write("DENIED\n");writer.flush();return;
            }
            writer.write("OK\n");writer.flush();
            socket.setSoTimeout(0);
            String message;
            while(running&&(message=reader.readLine())!=null) {
                String line=message;
                if(line.startsWith("MOVE ")){
                    String[] a=line.split(" ");
                    if(a.length==3) {
                        try {
                            float dx=Math.max(-240,Math.min(240,Float.parseFloat(a[1])));
                            float dy=Math.max(-240,Math.min(240,Float.parseFloat(a[2])));
                            ui.post(()->listener.onMove(dx,dy));
                        }catch(NumberFormatException ignored){}
                    }
                }else if("CLICK".equals(line))ui.post(listener::onClick);
                else if("BACK".equals(line))ui.post(listener::onBack);
                else if(line.matches("NAV (UP|DOWN|LEFT|RIGHT|SELECT)"))ui.post(()->listener.onNavigate(line.substring(4)));
            }
        }
    }
    public static String localIp() {
        try {
            Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
            String fallback="";
            while(nets.hasMoreElements()){
                NetworkInterface net=nets.nextElement();
                if(!net.isUp()||net.isLoopback())continue;
                Enumeration<InetAddress> addresses=net.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress addr=addresses.nextElement();
                    if(!(addr instanceof Inet4Address)||addr.isLoopbackAddress())continue;
                    String ip=addr.getHostAddress();
                    if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return ip;
                    fallback=ip;
                }
            }
            return fallback.isEmpty()?"Ver endereço nas definições Wi-Fi":fallback;
        }catch(Exception ex){return "Ver endereço nas definições Wi-Fi";}
    }
}
