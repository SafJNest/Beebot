//TODO RENDERE DECENTI LE FUNZIONI
async function printAll() {
    const response = await fetch("/rsc/commands.json");
    const aaa = await response.json();
    var json = JSON.parse(JSON.stringify(aaa));
    var command = document.getElementById("faker").value;
    console.log(command);
    document.getElementById("command").innerHTML = json[command]["help"];    
}

async function loadAll() {
    const response = await fetch("/rsc/commands.json");
    const aaa = await response.json();
    var json = JSON.parse(JSON.stringify(aaa));
    let commands = new Map();
    for (let key in json) {
      if(!commands.has(json[key]["category"])) {
        commands.set(json[key]["category"], new Array());
      }
      commands.get(json[key]["category"]).push(key);
    }
    let keys = Array.from(commands.keys());
    var containerCommands = document.createElement("div");
    containerCommands.className = "containerCommands";
    document.body.appendChild(containerCommands);
    for (var key in keys) {
      var category = document.createElement("div");
      category.className = "categories";
      var h1 = document.createElement("h1");
      h1.innerHTML = keys[key];
      category.appendChild(h1);
      containerCommands.appendChild(category);
      commands.get(keys[key]).forEach(function(name){
        command = json[name];
        var button = document.createElement("button");
        button.innerHTML = name;
        button.className = "collapsible";
        containerCommands.appendChild(button);
        var content = document.createElement("div");
        content.className = "content";
        content.style = "display:none";
        var table = document.createElement("table");
        table.className = "table";
        table.style="display:none";
        var tr = document.createElement("tr");
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
        tr.className = "inside";
        var td1 = document.createElement("td");
        var td2 = document.createElement("td");
        var td3 = document.createElement("td");
        var td4 = document.createElement("td");
        var td5 = document.createElement("td");
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
        td5.innerHTML = (command["cooldown"] == undefined) ? 0 : (command["cooldown"])+"s";
        tr.appendChild(td1);
        tr.appendChild(td2);
        tr.appendChild(td3);
        tr.appendChild(td4);
        tr.appendChild(td5);
        table.appendChild(tr);
        containerCommands.appendChild(content);

      });
    }
    sgozz();
      
    }
    
    function sgozz(){
      var coll = document.getElementsByClassName("collapsible");
      for (i = 0; i < coll.length; i++) {
          coll[i].addEventListener("click", function() {
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


