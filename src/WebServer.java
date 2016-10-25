import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class sourceList {
    String title;
    String d2rMappingFile;
    String simMappingFile;
    QueryConverter converter;

    void convInit() throws IOException {
        this.converter = new QueryConverter(simMappingFile);
    }
}

class WebServer {

    protected static void p(String s) {
        System.out.println(s);
    }

    protected static Properties props = new Properties();
    static Vector threads = new Vector();
    static File root;

    static int debug = 0;
    static int port = 80;
    static int timeout = 0;
    static int workers = 10;

    static String d2rMappingFile = "mapping.ttl";
    static String d2rQuery = "d2r-query.bat";
    static String d2rFormanOut = "text";

    static String simMappingFile = "smapping.txt";

    static sourceList[] source;

    static QueryConverter qCconv;

    static String indexPage;


    public static ServerSocket ss;








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
            r = props.getProperty("debug");
            if(r != null) {  debug = Integer.parseInt(r); }

            r = props.getProperty("port");
            if(r != null) {  port = Integer.parseInt(r); }

            r = props.getProperty("timeout");
            if(r != null) { timeout = Integer.parseInt(r);}

            r = props.getProperty("workers");
            if(r != null) {  workers = Integer.parseInt(r);}

            r = props.getProperty("d2rMappingFile");
            if(r != null) {   d2rMappingFile = r;  }

            r = props.getProperty("d2rQuery");
            if(r != null) {  d2rQuery = r;    }

            r = props.getProperty("d2rFormanOut");
            if(r != null) {  d2rFormanOut = r;   }

            r = props.getProperty("simMappingFile");
            if(r != null) {  simMappingFile = r;   }


            r = props.getProperty("source_enable");
            if(r != null) {
                String[] parts = r.replaceAll(" ","").split(";");
                source = new sourceList[parts.length];
                for (int i = 0; i < parts.length; ++i)
                {

                    sourceList s = new sourceList();

                    s.title = parts[i];

                    r = props.getProperty(parts[i]+"_d2rMappingFile");
                    if(r != null) { s.d2rMappingFile  = r;   }

                    r = props.getProperty(parts[i]+"_simMappingFile");
                    if(r != null) {
                        s.simMappingFile = r;
                        //s.converter =  new QueryConverter(r); // Инициализация конверторов для каждого источника данных
                    }

                    source[i] = s;

                }
            }



        }
    }

    static void printProps() {
        p("---- Config ------");
        p("root="+root);
        p("port="+port);
        p("timeout="+timeout);
        p("workers="+workers);

        p("d2rQuery="+d2rQuery);
        p("mappingFile="+d2rMappingFile);


        for (int i = 0; i < source.length; ++i)
        {
            p("\nSource title = "+source[i].title);
            p("Source d2rMappingFile = "+source[i].d2rMappingFile);
            p("Source simMappingFile = "+source[i].simMappingFile);
        }

        p("---------------");



    }

    public static void main(String[] a) throws Exception {
        loadProps();
        printProps();


        indexPage = getIndexPage();

        //p(indexPage);



        // Инициализация конверторов для каждого источника данных
        p("\n------Dictionary init---------\n");
        for (int i = 0; i < source.length; ++i){
            p("source "+source[i].title);
            source[i].convInit();
            p("\n");
        }


       // qCconv = new QueryConverter(simMappingFile);



        for(int i=0;i<workers;i++) {
            Worker w = new Worker();
            (new Thread(w, "worker #"+i)).start();
            threads.addElement(w);
        }
        ss = new ServerSocket(port);
        while (true) {
            Socket s = ss.accept();   Worker w = null;
            synchronized (threads) {
                if(threads.isEmpty()) {
                    Worker ws = new Worker();
                    ws.setSocket(s);
                    (new Thread(ws, "additional worker")).start();
                } else {
                    w = (Worker) threads.elementAt(0);
                    threads.removeElementAt(0);
                    w.setSocket(s);
                }
            }
        }



    }



    public static String getIndexPage(){
        String out = "";

        try(FileReader reader = new FileReader("index.html"))
        {
            // читаем посимвольно
            int c;
            while((c=reader.read())!=-1){

                out +=(char)c;
            }


        }
        catch(IOException ex){

            System.out.println(ex.getMessage());
        }


        return out;


    }


}

class Worker extends WebServer implements  Runnable {
    final static int BUF_SIZE = 2048;

    static final byte[] EOL = {(byte)'\r', (byte)'\n' };
    byte[] buf;
    private Socket s; Worker() {
        buf = new byte[BUF_SIZE];
        s = null;
    }

    synchronized void setSocket(Socket s) {
        this.s = s;
        notify();
    }

    public synchronized void run() {
        while(true) {
            if (s == null) {
                try{
                    wait();
                } catch (InterruptedException e) {
                    continue;
                }
            }
            try{
                handleClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            s = null;
            Vector pool = WebServer.threads;
            synchronized (pool) {
                if (pool.size() >= WebServer.workers) {
                    return;
                } else {
                    pool.addElement(this);
                }
            }
        }
    }





    // Обработка запроса
    void handleClient() throws IOException {

        s.setSoTimeout(WebServer.timeout);
        s.setTcpNoDelay(true);

        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));


        try{

            String getParams;

            // Получим первую строку заголовка
            String headerLine = in.readLine();

            // Работаем только с GET запросами остальное игнорируем
            if(headerLine != null && headerLine.startsWith("GET")){

                Pattern headerPattern = Pattern.compile("GET .+\\?query=(.+) HTTP.+");
                Matcher m = headerPattern.matcher(headerLine);
                if(m.matches()){

                    //getParams =  m.group(1);
                    //System.out.println("GET params = " + m.group(1));
                    //Map<String, String> params = splitQuery(getParams);
                    //writeResponse(params.toString()+"\n"+res, s.getOutputStream());

                    // Не знаю как из D2RQ получать вывод в переменную.
                    // Это раБотает, но выводит всЁ в консоль (что не удивительно)
                    // Чтоб работало в проекте должны быть все библиотеки из папки lib D2RQ
                    //String[] myStringArray = {mappingFile,"SELECT * { ?s ?p ?o } LIMIT 10"};
                    //d2rq.d2r_query.main(myStringArray);


                    String query =  URLDecoder.decode( m.group(1),"UTF-8");
                    //System.out.println("Query: " + query);

                    ////////////////////////////////////String res = runProcess(d2rQuery +" -f "+d2rFormanOut + "  " + d2rMappingFile +" \""+ query +"\"");
                    String res = "<plaintext >"+query;

                    if(d2rFormanOut.equals("json")){
                        res = "{\n\"query\": \""+query + "\",\n \"sources\": [\n";
                    }


                    p("\n\n------------------------------------------------------\nQuery = "+query);

                    // Выполнение запросов к базам
                    for (int i = 0; i < source.length; ++i)
                    {
                        String convertQuery = source[i].converter.convert(query);
                        p("\n----------- "+source[i].title + "convert  ------------- \nquery = "+convertQuery);

                        //String qres = runProcess(d2rQuery +" -f "+d2rFormanOut + " -b http://simm.com/ " + source[i].d2rMappingFile +" \""+ convertQuery +"\"");
                        String qres = runProcess(new String[]{d2rQuery ,"-f", d2rFormanOut ,"-b", "http://simm.com/" ,source[i].d2rMappingFile, convertQuery});

                        //p(qres);


                        if(d2rFormanOut.equals("json")){
                            res += "\t\t{\n\"source_"+i+"\": {\"query\": \""+convertQuery + "\", \"result\": "+qres+"\n\n}},";

                        }else{

                            res += "\n\n\n\n"+convertQuery;
                            res += "\n\n"+qres+"\n\n";
                        }

                    }

                    if(d2rFormanOut.equals("json")){
                        res = res.substring(0, res.length()-1);
                        res += "\n]}";
                    }


                   // if(debug > 0) res = query+"<p/>\n"+res;



                    writeResponse(res, s.getOutputStream());


                }else{
                    String err = "Bad input parametrs. Query not found.";
                  //  System.out.println("WARNING: "+err);
                  //  writeResponse(err, s.getOutputStream());
                    //writeResponse(indexPage, s.getOutputStream());
                    writeResponse(getIndexPage(), s.getOutputStream());


                }

            }else{
               // System.out.println("WARNING: GET params not found");
                // TODO отправка сообщения что работаем только с GET
            }


            // Распесатать остатки заголовка
            //while((headerLine = in.readLine()).length() > 0) {        System.out.println(headerLine);    }




        } catch (Throwable throwable) {

            throwable.printStackTrace();

        } finally {
            s.close();
        }
    }


    // Разобрать параметры URL
    public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }


    // Отправка ответа клиенту
    private void writeResponse(String s, OutputStream os) throws Throwable {
        String response = "HTTP/1.1 200 OK\r\n" +
                "Server: YarServer/2016-09-09\r\n" +
                "Content-Type: text/html\r\n" +
                //"Content-Length: " + (s.length()+34) + "\r\n" +
                "Connection: close\r\n\r\n";
        String result = response + s;
       // String result =  s;

        p(new String( result.getBytes( "UTF-8" )));

        System.out.println( result.length());

        os.write(result.getBytes(Charset.forName("UTF-8")));
        os.flush();


    }
/*
    HTTP/1.1 200 OK
    Last-Modified: Tue, 25 Sep 2007 21:40:05 GMT
    Expires: Tue, 25 Sep 2007 21:40:05 GMT
    Server: Azureus 3.0.2.2
    Connection: close
    Content-Type: text/xml; charset="utf-8"
    Content-Length: 339

    <?xml version="1.0" encoding="UTF-8"?>
*/

    // запустить в консоле
    private static String runProcess(String[] command) throws Exception {
        String out = "";
        Process pro = Runtime.getRuntime().exec(command);
        out += printLines(command + " stdout:", pro.getInputStream(), false);
        out += printLines(command + " stderr:", pro.getErrorStream(), true);
        pro.waitFor();
       // System.out.println(command + " exitValue() " + pro.exitValue());
        return out;
    }


    // Вывод
    private static String printLines(String name, InputStream ins, Boolean sysout) throws Exception {
        String line = null;
        String out = "";
        BufferedReader in = new BufferedReader(new InputStreamReader(ins));
        while ((line = in.readLine()) != null) {
            if(sysout) System.out.println(name + " " + line);
            out += line+"\n";
        }
        return out;
    }










}
