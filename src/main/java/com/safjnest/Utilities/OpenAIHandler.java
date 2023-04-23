package com.safjnest.Utilities;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;

public class OpenAIHandler {
    private static OpenAiService service;
    private static String maxTokens;
    private static String model;

    public OpenAIHandler(String key, String maxTokens, String model){
        OpenAIHandler.service = new OpenAiService(key);
        OpenAIHandler.maxTokens = maxTokens;
        OpenAIHandler.model = model;
        System.out.println("[OpenAI] INFO Connection Successful!");
    }

     /**
    * Useless method but {@link <a href="https://github.com/NeutronSun">NeutronSun</a>} is one
    * of the biggest bellsprout ever made
    */
	public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}

    public static CompletionRequest getCompletionRequest(String args){
        return CompletionRequest.builder()
        .prompt(args)
        .model(model)
        .maxTokens(Integer.valueOf(maxTokens))
        .topP(1.0)
        .frequencyPenalty(0.0)
        .presencePenalty(0.0)
        .bestOf(1)
        .echo(true)
        .build();
    }

    public static OpenAiService getAiService(){
        return service;
    }




}
