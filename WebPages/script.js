//print all the command info, reading the file json
function printAll() {
    var data = JSON.parse("commands.json");
    for (var i = 0; i < data.length; i++) {
        for (var j = 0; j < data[i].length; j++) {
            document.getElementById("command").innerHTML += data[i][j] + " ";
        }
        document.getElementById("command").innerHTML += "<br>";
    }
}