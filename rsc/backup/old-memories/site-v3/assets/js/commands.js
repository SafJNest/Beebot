window.onload = function() {
    console.log("loading commands");
    loadAll();
};

async function loadAll() {
    //get the file
    const response = await fetch("assets/js/commands.json");
    const responseJSON = await response.json();
    let json = JSON.parse(JSON.stringify(responseJSON));

    let privateCategories = ["Dangerous", "Only For Admin"]; //categories to not include in the website
    
    //puts the commands in a map grouped by category, excludes all commands in privateCategories
    let commands = new Map();
    for (let command in json) {
        if(privateCategories.includes(json[command]["category"])) continue;
        if (!commands.has(json[command]["category"])) {
            commands.set(json[command]["category"], new Array());
        }
        json[command]["name"] = command;
        commands.get(json[command]["category"]).push(json[command]);
    }

    let table = document.getElementsByClassName("table-commands")[0];

    //create the html div for each category and iterate over categories
    for (let [categoryName, category] of commands) {
        for(let command of category) {
            let tr = document.createElement("tr");
            let tdNmae = document.createElement("td");
            let tdHelp = document.createElement("td");
            let tdArgs = document.createElement("td");
            tr.appendChild(tdNmae);
            tr.appendChild(tdHelp);
            tr.appendChild(tdArgs);
            table.appendChild(tr);
            tdNmae.innerHTML = "/" + command["name"];
            tdHelp.innerHTML = command["help"];
            tdArgs.innerHTML = command["arguments"];
        }
    }
}