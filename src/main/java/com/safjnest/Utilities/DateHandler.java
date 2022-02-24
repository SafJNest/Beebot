package com.safjnest.Utilities;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class DateHandler {
    public static String formatDate(OffsetDateTime date){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy' 'HH:mm");
		
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(OffsetDateTime.now().toInstant().toEpochMilli() - date.toInstant().toEpochMilli());
        int years = calendar.get(Calendar.YEAR) - 1970;
        int months = calendar.get(Calendar.MONTH);
        int days = calendar.get(Calendar.DAY_OF_MONTH) - 1;

        String finalDate = dtf.format(date) + " (";
        if(years != 0){
            if(years == 1)
                finalDate += years + " anno ";
            else
                finalDate += years + " anni ";
        }
        if(months != 0){
            if(years != 0)
                finalDate += "e ";
            if(months == 1)
                finalDate += months + " mese ";
            else
                finalDate += months + " mesi ";
        }
        if(years == 0 && months == 0){
            if(days == 1)
                finalDate += days + " giorno ";
            else
                finalDate += days + " giorni ";
        }
        finalDate += "fa)";
        return finalDate;
    }
}
