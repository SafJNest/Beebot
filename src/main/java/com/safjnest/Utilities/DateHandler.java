package com.safjnest.Utilities;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class DateHandler {
    public static String formatDate(OffsetDateTime date){
        boolean hasY = false;
        boolean hasM = false;
        boolean hasD = false;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy' 'HH:mm");
		
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(OffsetDateTime.now().toInstant().toEpochMilli() - date.toInstant().toEpochMilli());
        int y = c.get(Calendar.YEAR)-1970;
        int m = c.get(Calendar.MONTH);
        int d = c.get(Calendar.DAY_OF_MONTH)-1;

        String finalDate = dtf.format(date) + " (";
        if(y != 0){
            hasY = true;
            if(y == 1)
                finalDate += y + " anno ";
            else
                finalDate += y + " anni ";
        }
        if(m != 0){
            hasM = true;
            if(m == 1)
                finalDate += m + " mese ";
            else
                finalDate += m + " mesi ";
        }
        if(d != 0){
            hasD = true;
            if(hasY || hasM)
                finalDate += "e ";
            if(d == 1)
                finalDate += d + " giorno ";
            else
                finalDate += d + " giorni ";
        }
        finalDate += "fa)";
        return finalDate;
    }
}
