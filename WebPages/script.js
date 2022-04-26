let lastOpen;

function init() {
  lastOpen = document.getElementsByClassName("home")[0];
  console.log(lastOpen);
}


function backToHome() {
  let home = document.getElementsByClassName("home")[0];
  if (alreadyOpen(home)) {
    return;
  }
  home.style.display = "inherit";
  lastOpen.style.display = "none";
  lastOpen = home;
}

function backToCommands() {
  lastOpen.style.display = "none";
  let containerCommands =
    document.getElementsByClassName("containerCommands")[0];
  if (containerCommands == undefined) {
    loadAll();
  } else if (!alreadyOpen(containerCommands)) {
    containerCommands.style.display = "inherit";
    lastOpen = document.getElementsByClassName("containerCommands")[0];
  }
}

function backToDocuments() {
  lastOpen.style.display = "none";
  let documents = document.getElementsByClassName("documents")[0];
  if (!alreadyOpen(documents)) {
    documents.style.display = "inherit";
    lastOpen = document.getElementsByClassName("documents")[0];
  }
}

function alreadyOpen(div) {
  if (div.style.display != "none") {
    return true;
  }
  return false;
}

async function loadAll() {
  //get the file
  const response = await fetch("/rsc/commands.json");
  const aaa = await response.json();
  var json = JSON.parse(JSON.stringify(aaa));
  //put all the commands in a map grouped by category
  let commands = new Map();
  for (let key in json) {
    if (!commands.has(json[key]["category"])) {
      commands.set(json[key]["category"], new Array());
    }
    commands.get(json[key]["category"]).push(key);
  }
  //create the html div
  var containerCommands = document.createElement("div");
  containerCommands.className = "containerCommands";
  containerCommands.style.display = "inherit";
  lastOpen = containerCommands;
  document.body.appendChild(containerCommands);
  //create the html div for each category and iterate over categories
  let keys = Array.from(commands.keys());
  for (var key in keys) {
    var category = document.createElement("div");
    var h1 = document.createElement("h1");
    category.className = "categories";
    h1.innerHTML = keys[key];
    category.appendChild(h1);
    containerCommands.appendChild(category);
    //iterate over the commands of the category "key"
    //name = command's name
    commands.get(keys[key]).forEach(function (name) {
      command = json[name];
      var button = document.createElement("button");
      var content = document.createElement("div");
      var table = document.createElement("table");
      var tr = document.createElement("tr");
      button.innerHTML = name;
      button.className = "collapsible";
      containerCommands.appendChild(button);
      content.className = "content";
      content.style = "display:none";
      table.className = "table";
      table.style = "display:none";
      tr.className = "top";
      var td1 = document.createElement("td");
      var td2 = document.createElement("td");
      var td3 = document.createElement("td");
      var td4 = document.createElement("td");
      var td5 = document.createElement("td");
      td1.style = "border-radius: 15px 0px 0px 0px; border-top:0px;";
      td2.style = "border-top:0px;";
      td3.style = "border-top:0px;";
      td4.style = "border-top:0px;";
      td5.style = "border-radius: 0px 15px 0px 0px; border-top:0px;";
      td1.innerHTML = "Help";
      td2.innerHTML = "Arguments";
      td3.innerHTML = "Category";
      td4.innerHTML = "Aliases";
      td5.innerHTML = "Cooldown";
      tr.appendChild(td1);
      tr.appendChild(td2);
      tr.appendChild(td3);
      tr.appendChild(td4);
      tr.appendChild(td5);
      table.appendChild(tr);
      containerCommands.appendChild(table);
      var tr = document.createElement("tr");
      var td1 = document.createElement("td");
      var td2 = document.createElement("td");
      var td3 = document.createElement("td");
      var td4 = document.createElement("td");
      var td5 = document.createElement("td");
      tr.className = "inside";
      td1.style = "border-radius: 0px 0px 0px 15px;";
      td5.style = "border-radius: 0px 0px 15px 0px;";
      td1.innerHTML = command["help"];
      td2.innerHTML = command["arguments"];
      td3.innerHTML = command["category"];
      //iterate all the aliases
      var aliases = command["alias"];
      var alias = "";
      for (var i = 0; i < aliases.length; i++) {
        alias += aliases[i];
        if (i != aliases.length - 1) {
          alias += ", ";
        }
      }
      td4.innerHTML = alias;
      td5.innerHTML = command["cooldown"] == undefined ? 0 : command["cooldown"] + "s";
      tr.appendChild(td1);
      tr.appendChild(td2);
      tr.appendChild(td3);
      tr.appendChild(td4);
      tr.appendChild(td5);
      table.appendChild(tr);
      containerCommands.appendChild(content);
    });
  }
  setListenerCollapsible();
}

function setListenerCollapsible() {
  var coll = document.getElementsByClassName("collapsible");
  for (i = 0; i < coll.length; i++) {
    coll[i].addEventListener("click", function () {
      this.classList.toggle("active");
      var content = this.nextElementSibling;
      if (content.style.display === "block") {
        content.style.display = "none";
      } else {
        content.style.display = "block";
      }
    });
  }
}


