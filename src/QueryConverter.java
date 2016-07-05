import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class QueryConverter {


    private Map<String, String> dictionary = new HashMap<String, String>();


    QueryConverter(String fileName) throws IOException {

        String content = readFile(fileName);

        // Зачитать файл в Map dictionary
        //    [{"os_uri":"http://simm.com/1b330b1d-b1a7-d2ef-4085-b59c6c38044d#gps/long","od_uri":"http://cubas.rttn.ru/TTO#Organization/Email"},{"os_uri":"http://simm.com/1b330b1d-b1a7-d2ef-4085-b59c6c38044d#channel/name","od_uri":"http://cubas.rttn.ru/TTO#Organization/Name"}]


        for(String keyValue : content.split("\\},\\{")) {
            String[] link = keyValue.split(",");

            // Заполнить словарь
            try {
                String key  =   link[0].substring(link[0].indexOf("\":\"")+3, link[0].length()-1);
                String val  =   link[1].substring(link[1].indexOf("\":\"")+3, link[1].length()-1);
                dictionary.put(key, val);
                System.out.println(key  + "\t=>\t"+ val);
            }catch (Exception e){
                System.out.println("WARNING: in smapping  "+ keyValue);
            }
        }

    }



    String convert (String q) {
        String res = null;

        if (dictionary.get(q) != null){
            res = dictionary.get(q);
        }

        return res;
    }



    private String readFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader (file));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");

        try {
            while((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        } finally {
            reader.close();
        }
    }



}
