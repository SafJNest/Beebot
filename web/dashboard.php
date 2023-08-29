<?php
session_start();
?>

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js"></script>
    <script src="../javascript/websocket.js"></script>
    <link rel="stylesheet" href="../css/style.css">
    <link rel="shortcut icon" type="image/x-icon" href="../favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Beebot</title>
</head>

<body>
    <div class="header">
        <div class="logo">
                <div class="beebot-icon">
                    <img class="beebot-logo" src="../beebot-removebg-preview.png"></img>
                </div>
                <div class="beebot-name">
                    Beebot
                </div>
            </div>
        <?=printUser();?>
    </div>
    <div class="container">
        <div>
            <h1 id="name">
                Client
            </h1>
        </div>
        <div id="status">
            Not connected
        </div>
        <div class="guilds">
        
        </div>
    </div>
    <script>
        async function load(userId, token, accessToken) {
            openSocket(userId);
            fetch("https://discord.com/api/users/@me/guilds", {
                headers: {
                    authorization: `${token} ${accessToken}`,
                },
            })
                .then(result => result.json())
                .then(response => {
                    console.log(response)
                    //$(".guilds").append("<button>" + guild.name + "</button>");
                    let idsToCheck = "checkBeebot-"+userId+"-";
                    let ids ="";
                    response.forEach((guild) => {
                        if (guild.permissions == "2147483647")
                            ids += guild.id + "/";
                    });
                    sendMsg(idsToCheck+ids)

                })
                .catch(console.error);
        }

        function loadGuild(id){
            window.location.href =id;
        }

        function base_url(){
                let url = document.location.origin;
                if(url.includes('localhost')){
                    url += '/phpmyadmin/safjweb';
                }
                return url;
            }
    </script>
    <?php
        require __DIR__ . '/database.php';
        $data = $_SESSION['user_data'];
        $user_id = $data['id'];
        $token = $data['tokenType'];
        $accessToken = $data['accessToken'];
        echo '<script>load("'. $user_id .'", "'. $token .'", "'. $accessToken .'");</script>';

        function printUser(){
            $icon_url = 'https://cdn.discordapp.com/avatars/'. $_SESSION['user_data']['id'] .'/'. $_SESSION['user_data']['avatar'] .'.png';
            echo '
            <div class="user">
                <div class="user-icon">
                    <img src="'. $icon_url .'"></img>
                </div>
                <div class="user-name">
                    '. $_SESSION['user_data']['global_name'] .'
                </div>
            </div>
            
            ';
        }
    ?>
</body>

</html>