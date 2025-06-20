<?php
    require __DIR__ . '../../assets/php/database.php';
    require __DIR__ . '../../assets/php/utilities.php';
    session_start();
    $data = $_SESSION['user_data'];
    $user_id = $data['id'];



    echo '<div class="prefix">

    </div>
    <div>
        <input type="text" id="newPrefix" name="name" required minlength="4" maxlength="8" size="10" />
    </div>
    <div>
        <button onclick="commit()">Change</button>
    </div>';
    echo '<script>load("'. $_SESSION['guild_id'] .'", "'. $user_id .'");</script>'


?>

<script>
     function commit(){
        let prefix = $('#newPrefix').val();
        let request = '{"request":"newPrefix","guildId":"'+ guildId +'","userId":"'+ userId +'","prefix":"'+ prefix +'"}';
        openSocket(request);
    }
</script>