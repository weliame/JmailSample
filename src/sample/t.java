import java.security.*;

public class t {
    public static void main(String[] argv) throws Exception {
	com.sun.mail.auth.OAuth2SaslClientFactory.init();
	com.sun.mail.auth.OAuth2SaslClientFactory.init();
	Provider[] pp = Security.getProviders();
	for (Provider p : pp)
	    System.out.println(p.getName());
    }
}
