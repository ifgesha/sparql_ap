import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class QueryConverter {


    private Map<String, String> dictionary = new HashMap<String, String>();


    // Конструктор  вычитывает файл с маппингом превращая его в словарь
    // попутно произволдя преобразования из формата CUBA в  формат d2rq
    QueryConverter(String fileName) throws IOException {

        // Прочитать файл и убрать пробелы и перевод строк
        String content = readFile(fileName);
        content = content.replaceAll(" ","");
        content = content.replaceAll("\\n|\\r\\n","");

        // Зачитать файл в Map dictionary
        for(String keyValue : content.split("\\},")) {
            String[] link = keyValue.split(",");

            // Заполнить словарь
            try {
                // Выделить ключь и значение
                String val  =   link[0].substring(link[0].indexOf("\":\"")+3, link[0].lastIndexOf("\""));
                String key  =   link[1].substring(link[1].indexOf("\":\"")+3, link[1].lastIndexOf("\""));
                //System.out.println(key  + "\t=>\t"+ val);

                // Сконвертировать значение в формат d2rq
                //val = val.replaceAll("(.+)/.+#(\\w+)/(\\w+)$", "$1/vocab/$2_$3");
                val = val.replaceAll("(.+)/.+#(\\w+)/(\\w+)$", "vocab/$2_$3");
                System.out.println(key  + "\t=>\t"+ val);

                dictionary.put(key, val);

            }catch (Exception e){
                System.out.println("WARNING: smapping file has error "+ keyValue);
            }
        }

    }


    // Замена элементов запроса из словаря
    String convert (String q) {


        for(Map.Entry<String, String> entry : dictionary.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            q = q.replace(key, value);
        }


        return q;
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
