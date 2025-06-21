<?php
session_start();
$page = $_GET['page'] ?? 'home';

if(isset($_GET['id']))
    $_SESSION['guild_id'] = $_GET['id'];

echo "DIOCANE ".$page;
?>

<!DOCTYPE html>
<html lang="en">

    <head>
        <meta charset="UTF-8">
        <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>

        <script src="/assets/js/script.js"></script>
        <script src="/assets/js/websocket.js"></script>
        <script src="/assets/js/functions.js"></script>

        <link rel="stylesheet" href="/assets/css/style-dashboard.css">

        <link rel="shortcut icon" type="image/x-icon" href="/assets/img/favicon.ico">

        
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Beebot</title>
    </head>

    <body>
        <div class="sidenav">
            <a id="sidebar" data-page="home">home</a>
            <a id="sidebar" data-page="prefix">Prefix</a>
            <a id="sidebar" data-page="welcome">Welcome Message</a>
            <a id="sidebar" data-page="goodbye">Goodbye Message</a>
            <a id="sidebar" data-page="levels">Levels</a>
        </div>
        <div class="page-container">
            <div class="header">
                <div class="logo">
                        <div class="beebot-icon">
                            <img class="beebot-logo" src="/assets/img/beebot-removebg-preview.png"></img>
                        </div>
                        <div class="beebot-name">
                            Beebot
                        </div>
                    </div>
                <?=printUser();?>
            </div>

            <div class="container">
                

            </div>
        </div>
        <script>

            $(document).ready(function () {
                $('[data-page]').each(function () {
                    $(this).on('click', function () {
                        var page = $(this).data('page');
                        let id = <?= $_SESSION['guild_id']?>;
                        loadPage(id, page);
                    });
                });
            });

            var guildId;
            var userId;

            function loadPage(id, page){
                $.ajax({
                    type: 'POST',
                    url: '/dashboard_files/'+page+'.php',
                    data: {},
                    success: function(content) {
                        $('.container').html(content);
                        var newUrl = baseUrl() + '/dashboard/' + id + '/' + page;
                        history.pushState({ path: newUrl }, '', newUrl);
                    }
                });
            }


            function load(guild_id, user_id){
                guildId = guild_id;
                userId =  user_id;
                loadingBee();
                
                let request = '{"request":"getPrefix","guildId":"'+ guildId +'"}';
                openSocket(request);
            }
        </script>

    </body>
</html>

<?php
    require __DIR__ . '/assets/php/database.php';
    $guild_id =  $_GET['id'];
    $_SESSION['guild_id'] = $guild_id;
    $data = $_SESSION['user_data'];
    $user_id = $data['id'];
    $token = $data['tokenType'];
    $accessToken = $data['accessToken'];
    if(isset($page)){
        echo '<script>loadPage("'. $guild_id .'", "'. $page .'");</script>';
    }
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

<style>

/* The sidebar menu */
.sidenav {
  height: 100%; /* Full-height: remove this if you want "auto" height */
  width: 160px; /* Set the width of the sidebar */
  position: fixed; /* Fixed Sidebar (stay in place on scroll) */
  z-index: 1; /* Stay on top */
  top: 0; /* Stay at the top */
  left: 0;
  background-color: #111; /* Black */
  overflow-x: hidden; /* Disable horizontal scroll */
  padding-top: 20px;
}

/* The navigation menu links */
.sidenav a {
  padding: 6px 8px 6px 16px;
  text-decoration: none;
  font-size: 25px;
  color: #818181;
  display: block;
}

/* When you mouse over the navigation links, change their color */
.sidenav a:hover {
  color: #f1f1f1;
}

/* Style page content */


/* On smaller screens, where height is less than 450px, change the style of the sidebar (less padding and a smaller font size) */
@media screen and (max-height: 450px) {
  .sidenav {padding-top: 15px;}
  .sidenav a {font-size: 18px;}
}

.page-container{
    margin-left: 200px;
    padding: 1px 16px;
}


</style>
