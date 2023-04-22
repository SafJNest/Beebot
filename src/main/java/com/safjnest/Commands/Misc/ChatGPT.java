package com.safjnest.Commands.Misc;


import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsHandler;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;


/**
 * Gets a list of aliases for a command.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.02
 */
public class ChatGPT extends Command {
    
    /**
     * Default constructor for the class.
     */
    public ChatGPT() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(CommandEvent event) {
        
        OpenAiService service = new OpenAiService("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");
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(event.getArgs())
            .model("text-curie-001")
            .maxTokens(200)
            .topP(1.0)
            .echo(true)
            .build();
        event.reply(service.createCompletion(completionRequest).getChoices().get(0).getText());

    }

}
