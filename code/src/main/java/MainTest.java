import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

public class MainTest {
    public static void main(String[] args) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName("www.baidu.com");
        System.out.println(ip);
    }
}
