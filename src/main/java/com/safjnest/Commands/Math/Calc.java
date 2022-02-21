package com.safjnest.Commands.Math;

import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2.5
 */
public class Calc extends Command{

    private static final Set<String> OP = Set.of("+","-","*","/");

    public Calc(){
        this.name = "calc";
        this.aliases = new String[]{"c", "calcolatore", "calcolatrice"};
        this.help = "Consente di effettuare semplici operazioni matematiche tra due numeri.";
        this.category = new Category("Matematica");
        this.arguments = "[calc] [(n+n) (n-n) (n*n) (n/n) (sqrt n) (sin n) (cos n ) (ln n) ]";
    }

    @Override
    protected void execute(CommandEvent event) {
        String command = event.getArgs();
        try {
            if(Character.isDigit(command.charAt(0))){
                Double a = 0.0, b = 0.0;
                char sign = 'a';
                System.out.println(command);
                for(int i = 0; i < command.length(); i++){
                    if(OP.contains(String.valueOf(command.charAt(i)))){
                        a = Double.parseDouble(command.substring(0, i));
                        b = Double.parseDouble(command.substring(i+1));
                        sign = command.charAt(i);
                        break;
                    }
                }
                switch(sign){
                    case '+':
                        event.reply(String.valueOf((a+b)));
                        break;
    
                    case '-':
                        event.reply(String.valueOf((a-b)));
                        break;
    
                    case '*':
                        event.reply(String.valueOf((a*b)));
                        break;
    
                    case '/':
                        event.reply(String.valueOf((a/b)));
                        break;
                }
            
            }else{
                String fun = "";
                Double a = 0.0;
                for(int i = 0; i < command.length(); i++){
                    if(Character.isDigit(command.charAt(i))){
                        fun = command.substring(0, i-1);
                        a = Double.parseDouble(command.substring(i));
                        break;
                    }
                }
                System.out.println(fun);
                System.out.println(a);
                switch(fun){
                    case "ln":
                        event.reply(String.valueOf(Math.log(a)));
                        break;
    
                    case "sqrt":
                        event.reply(String.valueOf(Math.sqrt(a)));
                        break;
    
                    case "sin":
                        event.reply(String.valueOf(Math.sin(Math.toRadians(a))));
                        break;
    
                    case "cos":
                        event.reply(String.valueOf(Math.cos(Math.toRadians(a))));
                        break;
                    default:
                        event.reply("La funzione non esiste, per ulteriori informazioni usare il comando help");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.reply("SYNTAX ERROR: " + e.getMessage());
        }
    }
}