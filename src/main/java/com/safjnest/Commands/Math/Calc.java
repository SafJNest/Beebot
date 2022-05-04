package com.safjnest.Commands.Math;

//import java.math.BigInteger;
import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

/**
 * This class is used to calculate the result of a mathematical expression.
 * <p>There is one way to use it:</p>
 * By typing <code>%calc</code> followed by the expression you want to calculate.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2.5
 */
public class Calc extends Command{
    /**
     * A set of all the operators that can be used in a mathematical expression.
     */
    private static final Set<String> OP = Set.of("+","-","*","/", "^");

    public Calc(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        String command = event.getArgs();
        try {
            if(Character.isDigit(command.charAt(0))){
                Double a = 0.0, b = 0.0;
                //BigInteger x = BigInteger.ZERO, y = BigInteger.ZERO;
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
                    
                    case '^':
                        event.reply(String.valueOf((Math.pow(a, b))));
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
                    case "tan":
                        event.reply(String.valueOf(Math.tan(Math.toRadians(a))));
                        break;
                    case "acos":
                        event.reply(String.valueOf(Math.acos(a)));
                        break;
                    case "asin":
                        event.reply(String.valueOf(Math.asin(a)));
                        break;
                    case "atan":
                        event.reply(String.valueOf(Math.atan(a)));
                        break;
                    case "exp":
                        event.reply(String.valueOf(Math.exp(a)));
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