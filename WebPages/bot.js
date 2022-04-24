const Stats  = require('discord-live-stats');
const Discord = require('discord.js');
const client = new Discord.Client()

const Poster = new Stats.Client(client, {
    stats_uri: 'http://localhost:3000/',
    authorizationkey: "OTM5ODc2ODE4NDY1NDg4OTI2.Yf_Ofw.1Ql5INVXqLSPXYG7OxRaCD5A8bU",
})
/*
* YOUR BOT CODE STUFF
*/
client.login(`Your_Bot_Token`)
console.log("all ok")   //this line is just to make sure the bot is working
