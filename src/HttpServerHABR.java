import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.Vector;


public class HttpServerHABR {

    protected static Properties props = new Properties();
    static File root;
    static int port = 80;
    static int timeout = 0;
    static int workers = 10;


    public static void main(String[] args) throws Throwable {

       // loadProps();


        ServerSocket ss = new ServerSocket(8080);
        while (true) {
            Socket s = ss.accept();
            System.err.println("Client accepted");
            new Thread(new SocketProcessor(s)).start();
        }
    }


    protected static void _log(String s) {
        System.out.println(s);
    }

   /*

    static void loadProps() throws IOException {
        File f = new File(System.getProperty("user.dir")+File.separator+"WebServer.ini");
        if(f.exists()) {
            InputStream is=new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("root");
            if(r != null) {
                root = new File(r);
                if(!root.exists()) {
                    throw new Error(root + " doesn't exist as server root");
                }
            }
            r = props.getProperty("port");
            if(r != null) {
                port = Integer.parseInt(r);
            }
            r = props.getProperty("timeout");
            if(r != null) {
                timeout = Integer.parseInt(r);
            }
            r = props.getProperty("workers");
            if(r != null) {
                workers = Integer.parseInt(r);
            }
        }
    }

    static void printProps() {
        _log("root="+root);
        _log("port="+port);
        _log("timeout="+timeout);
        _log("workers="+workers);
    }


    */










    private static class SocketProcessor implements Runnable {

        private Socket s;
        private InputStream is;
        private OutputStream os;

        private SocketProcessor(Socket s) throws Throwable {
            this.s = s;
            this.is = s.getInputStream();
            this.os = s.getOutputStream();
        }

        public void run() {
            try {
                readInputHeaders();
                writeResponse("<html><body><h1>Hello from Habrahabr</h1></body></html>");
            } catch (Throwable t) {
                /*do nothing*/
            } finally {
                try {
                    s.close();
                } catch (Throwable t) {
                    /*do nothing*/
                }
            }
            System.err.println("Client processing finished");
        }

        private void writeResponse(String s) throws Throwable {
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Server: YarServer/2009-09-09\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: " + s.length() + "\r\n" +
                    "Connection: close\r\n\r\n";
            String result = response + s;
            os.write(result.getBytes());
            os.flush();
        }

        private void readInputHeaders() throws Throwable {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            while(true) {
                String s = br.readLine();
                if(s == null || s.trim().length() == 0) {
                    break;
                }else{
                    System.out.println(s);

                }
            }
        }
    }
}