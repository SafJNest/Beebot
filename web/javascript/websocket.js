let socket;
let id;



function openSocket(id){
    socket = new WebSocket("ws://192.99.45.100:8096/ws");
    id = id;
    console.log("connected?");
    
    socket.addEventListener("open", (event) => {
        socket.send("connected-" + id); 
        //socket.send("server_list-" + id); 
        let div = document.getElementById("status");
        div.innerHTML = "connected";

    });
    socket.addEventListener("close", (event) => {
        let div = document.getElementById("status");
        div.innerHTML = "disconnected";
        console.log("Connection closed:", event);
    });
    
    socket.onmessage = (event) => {
        if(event.data.startsWith("connected")){
            let id = event.data.split("-")[1];
            let h1 = document.getElementById("name");
            h1.innerHTML = "Client " + id;
            return;
        }else if(event.data.startsWith("server_list")){
            let json = JSON.parse(customSplitByCharacter(event.data, "-", 2)[1]);
            console.log(json);
            for(let i = 0; i < json.length; i++){
                let img =  '<img src="' + json[i].icon + '">' +
                '</img>';
                if(json[i].icon == "null"){
                    img =  '<div style=" text-align: center;top: 30PX;POSITION: RELATIVE;">' + String(json[i].name).charAt(0) + '</div>';
                }

                $('.guilds').append('' +
                    '<div class="guild">' +
                        '<div>' +
                            '<table>' +
                                '<tbody>' +
                                    '<tr>' +
                                        '<td>' +
                                        '<div class="imgGuild">' +
                                            img +
                                        '</div>' +
                                        '</td>' +
                                        '<td>' +
                                        '<div>' +
                                            json[i].name  +
                                        '</div>' +
                                        '</td>' +
                                    '</tr>' +
                                '</tbody>' +
                            '</table>' +
                        '</div>' +
                    '</div>'
                );
            }
            
            return;
        }
        if(event.data == "new"){
            alert("new client connected");
            return;
        }
    };

    

}   

    function sendMsg(msg){
        socket.send(msg);
    }

    function send(){
        let string = document.getElementById("input").value;
        socket.send(string);
    }


    function customSplitByCharacter(inputString, splitChar, n) {
        if (n <= 1) {
            return [inputString];
        }
        n--;
        const parts = inputString.split(splitChar);
        const resultArray = [];
    
        for (let i = 0; i < Math.min(n, parts.length); i++) {
            resultArray.push(parts[i]);
        }
    
        if (n < parts.length) {
            const remainingPart = parts.slice(n).join(splitChar);
            resultArray.push(remainingPart);
        }
    
        return resultArray;
    }
