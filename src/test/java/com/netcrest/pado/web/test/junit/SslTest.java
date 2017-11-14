package com.netcrest.pado.web.test.junit;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;

public class SslTest
{	
	@Test
	public void testTrustedKeyStore() throws Exception
	{
		String trustedKeyStore = "trusted.keystore";
		String password = "secret";
		
	    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
	    InputStream keystoreStream = ClassLoader.getSystemResourceAsStream(trustedKeyStore);
	    keystore.load(keystoreStream, password.toCharArray());
	    trustManagerFactory.init(keystore);
	    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
	    SSLContext sc = SSLContext.getInstance("SSL");
	    sc.init(null, trustManagers, null);
	    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());  
	}

}
