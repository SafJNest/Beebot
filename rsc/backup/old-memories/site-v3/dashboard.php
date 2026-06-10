<?php
session_start();
?>

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js"></script>
    <script src="../assets/js/script.js"></script>
    <script src="../assets/js/websocket.js"></script>
    <script src="../assets/js/functions.js"></script>
    <link rel="stylesheet" href="../assets/css/bulma.min.css" />
    <link rel="stylesheet" href="../assets/css/style-home.css" />
    <link rel="stylesheet" href="https://unpkg.com/aos@2.3.1/dist/aos.css" />
    <link rel="stylesheet" href="https://unpkg.com/tippy.js@6/animations/scale.css" />
    <link rel="stylesheet" href="../assets/css/style-dashboard.css">
    <link rel="shortcut icon" type="image/x-icon" href="../assets/img/favicon.ico" />
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.2/css/all.min.css" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Beebot</title>
</head>

<body>
        <!-- Navbar Start -->
        <nav
      class="navbar is-fixed-top"
      role="navigation"
      aria-label="main navigation"
    >
      <div class="navbar-brand mt-2 mb-2">
        <a class="navbar-item" href="#">
          <img src="../assets/img/beebot-b.png"></img>
        </a>

        <a
          role="button"
          class="navbar-burger has-text-white"
          data-target="navMenu"
          aria-label="menu"
          aria-expanded="false"
        >
          <span aria-hidden="true"></span>
          <span aria-hidden="true"></span>
          <span aria-hidden="true"></span>
        </a>
      </div>

      <div id="navbarBasicExample" class="navbar-menu">
        <div class="navbar-start">
          <a href="#" class="navbar-item is-tab">
            Home
          </a>

          <a href="#features" class="navbar-item is-tab">
            Features
          </a>

          <a href="#stats" class="navbar-item is-tab">
            Stats
          </a>

          <a href="#" class="navbar-item is-tab">
            Docs
          </a>
        </div>

        <div class="navbar-end">
          <a href="#" class="navbar-item is-tab" target="_blank">
            <i class="fa-brands fa-discord"></i>
          </a>

          <a href="#" class="navbar-item is-tab" target="_blank">
            <i class="fa-brands fa-github"></i>
          </a>

          <div class="navbar-item">
            <div class="buttons">
                <?=printUser();?>
            </div>
          </div>
        </div>
      </div>
    </nav>

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
            fetch("https://discord.com/api/users/@me/guilds", {
                headers: {
                    authorization: `${token} ${accessToken}`,
                },
            })
                .then(result => result.json())
                .then(response => {
                    let ids ="";
                    response.forEach((guild) => {
                        if (guild.permissions == "2147483647")
                            ids += guild.id + "/";
                    });
                    let request = '{"request":"server_list","ids":"'+ ids +'"}';
                    openSocket(request);

                })
                .catch(console.error);
        }

        function loadGuild(id){
            window.location.href = '/dashboard/' + id+'/home';
        }

    </script>


    <?php
        require __DIR__ . '/assets/php/database.php';
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