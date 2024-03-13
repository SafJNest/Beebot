




function openSocket(msg){
    let socket = new WebSocket("ws://192.99.45.100:8096/ws");

    socket.addEventListener("open", (event) => {
        let request = msg;
        console.log("SENT: " + request);
        socket.send(request);

    });
    socket.addEventListener("close", (event) => {

    });
    
    socket.onmessage = (event) => {
        console.log("RECEIVED: " + event.data);
        if(event.data.startsWith("server_list")){
            let json = JSON.parse(customSplitByCharacter(event.data, "-", 2)[1]);
            for(let i = 0; i < json.length; i++){
                let img =  '<img src="' + json[i].icon + '">' +
                '</img>';
                if(json[i].icon == "null"){
                    img =  '<div style=" text-align: center;top: 30PX;POSITION: RELATIVE;">' + String(json[i].name).charAt(0) + '</div>';
                }

                $('.guilds').append('' +
                    '<div class="guild" onclick="loadGuild(\''+String(json[i].id).trim()+'\')">' +
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
            
           
        }else if(event.data.startsWith("getHomeStats")){
            let json = JSON.parse(customSplitByCharacter(event.data, "-", 2)[1]);
            $('#servers').html(json['cont_guilds']);
            $('#users').html(json['cont_user']);
            $('#commands').html(json['cont_command']);
        }else if(event.data.startsWith("getPrefix")){
            let json = JSON.parse(customSplitByCharacter(event.data, "-", 2)[1]);
            $('.prefix').html(json['prefix']);
        }else if(event.data.startsWith("newPrefix")){
            let a = customSplitByCharacter(event.data, "-", 2)[1];
            if(a == 'ok'){
                alert("ye!");
            }else{
                alert("!ye");
            }
        }
        socket.close();
    };

    

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
