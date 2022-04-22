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
    //iterate json
    for (var command in json) {
        var button = document.createElement("button");
        button.innerHTML = command;
        button.className = "collapsible";
        document.body.appendChild(button);
        var content = document.createElement("div");
        content.className = "content";
        content.style = "display:none";
        document.body.appendChild(content);
        var p = document.createElement("p");
        p.innerHTML = json[command]["help"];
        content.appendChild(p);
         
    }
  
}

function sgozz(){
    var coll = document.getElementsByClassName("collapsible");
    var i;
    
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


