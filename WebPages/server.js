const Stats  = require('discord-live-stats');

const express = require("express");
const app = express();

const client = new Stats.Server(app, {
    bot: {
        name: "TheBeeBoxCanary",
        icon: "Your Discord Bot Avatar URL",
        website: "Your Website URL",
        client_id: "939876818465488926",
        client_secret: "MtnOOpbFsBwk5JDByY0yTu_AnhEzoFfY"
    },
    stats_uri: "http://localhost:3000/", //Base URL
    redirect_uri: "http://localhost:3000/login", //Landing Page
    owners: ["939876818465488926"],
    authorizationkey: "faker",
})

client.on('error', console.log)

app.listen(3000, () => {
  console.log("Application started, listening on port 3000!");
});