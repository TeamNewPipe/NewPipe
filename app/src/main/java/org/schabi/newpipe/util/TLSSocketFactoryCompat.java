package org.schabi.newpipe.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;


/**
 * This is an extension of the SSLSocketFactory which enables TLS 1.2 and 1.1.
 * Created for usage on Android 4.1-4.4 devices, which haven't enabled those by default.
 */
public class TLSSocketFactoryCompat extends SSLSocketFactory {


    private static TLSSocketFactoryCompat instance=null;

    private SSLSocketFactory internalSSLSocketFactory;

    public static TLSSocketFactoryCompat getInstance() throws NoSuchAlgorithmException, KeyManagementException {
        if(instance!=null)
            return instance;
        return instance=new TLSSocketFactoryCompat();
    }


    public TLSSocketFactoryCompat() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        internalSSLSocketFactory = context.getSocketFactory();
    }

    public TLSSocketFactoryCompat(TrustManager[] tm) throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tm, new java.security.SecureRandom());
        internalSSLSocketFactory = context.getSocketFactory();
    }

    public static void setAsDefault() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(getInstance());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket) {
        if(socket != null && (socket instanceof SSLSocket)) {
            /*
            //Create list of supported protocols
            ArrayList<String> supportedProtocols = new ArrayList<>();
            for (String protocol : ((SSLSocket)socket).getEnabledProtocols()) {

                //Log.d("TLSSocketFactory", "Supported protocol:" + protocol);
                //Only add TLS protocols (don't want ot support older SSL versions)
                if (protocol.toUpperCase().contains("TLS")) {
                    supportedProtocols.add(protocol);
                }
            }
            //Force add TLSv1.1 and 1.2 if not already added
            if (!supportedProtocols.contains("TLSv1.1")) {
                supportedProtocols.add("TLSv1.1");
            }
            if (!supportedProtocols.contains("TLSv1.2")) {
                supportedProtocols.add("TLSv1.2");
            }

            String[] protocolArray = supportedProtocols.toArray(new String[supportedProtocols.size()]);

            //enable protocols in our list
            //((SSLSocket)socket).setEnabledProtocols(protocolArray);
            */
            // OR: only enable TLS 1.1 and 1.2!
            ((SSLSocket)socket).setEnabledProtocols(new String[] {"TLSv1.1", "TLSv1.2"});

        }
        return socket;
    }
}