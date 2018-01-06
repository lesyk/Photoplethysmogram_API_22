package io.github.kaisubr.photoplethysmogram_api_22.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Photoplethysmogram_API_22, file created in io.github.kaisubr.photoplethysmogram_api_22.data by Kailash Sub.
 */
public class LineChartGenerator {
    public static Bitmap getBitmap(List<Double> x, List<Double> y, int width, int height) throws IOException {
        /**
         cht=lxy
         chs=320x200
         chd=t:0,10,20,30,40,50,90|0,10,40,90,70,42,300
         chxt=x,x,y,y
         chxr=0,0,30,5
         2,20,50,5
         chxs=0,000000,10,0,lt
         2,000000,10,0,lt
         1,,10,-3.5,lt
         3,,10,0,lt
         chxl=1:|Time+(seconds)|
         3:|+
         */
        //http://chart.googleapis.com/chart?cht=lxy&chs=320x200&chd=t:0,10,20,30,40,50,90|0,10,40,90,70,42,300&chxt=x,x,y,y&chxr=0,0,30,5|2,20,50,5&chxs=0,000000,10,0,lt|2,000000,10,0,lt|1,,10,-3.5,lt|3,,10,0,lt&chxl=1:|Time+(seconds)||3:|+
        final String AND = "&";

        StringBuilder sb = new StringBuilder();
        sb.append("http://chart.googleapis.com/chart?cht=lxy&chs=" + width + "x" + height + AND);
        sb.append("chd=t:");
        for (int i = 0; i < x.size(); i++) {
            sb.append(x.get(i) + ((i != (x.size() - 1))? "," : ""));
        }
        sb.append("|");
        for (int i = 0; i < y.size(); i++) {
            sb.append(y.get(i) + ((i != (y.size() - 1))? "," : ""));
        }
        sb.append(AND);     // x - range     | y - range
                            // &chxr=0,0,30,5|2,20,50,5
        sb.append("chxt=x,x,y,y&chxr=0,0,30,5|2,0,50,10&chxs=0,000000,10,0,lt|2,000000,10,0,lt|1,,10,-5.5,lt|3,,10,0,lt&chf=bg,s,EFEFEF&chxl=1:|Time+(seconds)||3:|+");
        System.out.println(sb.toString());
        System.out.println(x + "\n" + y);
        return BitmapFactory.decodeStream((InputStream) new URL(sb.toString()).getContent());
    }
}
