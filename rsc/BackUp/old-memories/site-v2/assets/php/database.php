<?php

$servername = "na01-sql.pebblehost.com";
$username = "customer_444048_beebot";
$password = "ExtremeSafJDatabase117@";
$dbname = "customer_444048_beebot";

global $conn;
$conn = new mysqli($servername, $username, $password, $dbname);

global $beebot;
$beebot = '938487470339801169';

if ($conn->connect_error) {
  die("Connection failed: " . $conn->connect_error);
}

function getUserSounds($user_id) {
  global $conn;
  $sql = "SELECT id, name FROM sound WHERE user_id = $user_id";
  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      echo "id: " . $row["id"]. " - Name: " . $row["name"]. "<br>";
    }
  } else {
    echo "0 results";
  }
}

function getGuildSettings($guild_id) {
  global $conn;
  global $beebot;
  $sql = "SELECT prefix, name_tts, language_tts, exp_enabled FROM guild_settings WHERE guild_id = $guild_id AND bot_id = $beebot";
  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      echo "prefix: " . $row["prefix"]. " - name_tts: " . $row["name_tts"]. " - language_tts: " . $row["language_tts"]. " - exp_enabled: " . $row["exp_enabled"]."<br>";
    }
  } else {
    echo "0 results";
  }
}

function getWelcomeMessage($guild_id) {
  global $conn;
  global $beebot;

  $arr = [];
  $sql = "SELECT message_text, channel_id FROM welcome_message WHERE guild_id = $guild_id AND bot_id = $beebot";
  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $arr = ['message'=>$row["message_text"], 'channel'=>$row["channel_id"]];
    }
  }

  $sql = "SELECT role_id FROM welcome_roles WHERE guild_id = $guild_id AND bot_id = $beebot";
  $result = $conn->query($sql);

  $roles = [];
  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $roles[$row['role_id']] = ['role_id'=>$row['role_id']];
    }
  }
  return $arr + ['roles'=>$roles];
}

function getGoodbyeMessage($guild_id) {
  global $conn;
  global $beebot;

  $arr = [];
  $sql = "SELECT message_text, channel_id FROM left_message WHERE guild_id = $guild_id AND bot_id = $beebot";
  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $arr = ['message'=>$row["message_text"], 'channel'=>$row["channel_id"]];
    }
  }

  return $arr;
}

function getCommand($guild_id, $date = null, $user = null) {
  global $conn;
  global $beebot;

  $query_add = "";
  if($user != null){
    $query_add = "AND user_id = $user";
  }
  if($date != null){
    $query_add .= " AND UNIX_TIMESTAMP(time) >= $date";
  }

  $arr = [];
  $sql = "select * from command_analytic where guild_id = $guild_id  $query_add";

  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $arr[strtotime("0:00", timestamp_unix($row['time']))][] = $row['name'];
    }
  }

  return $arr;
}

function getLevelsData($guild_id) {
  global $conn;
  global $beebot;


  $arr = [];
  $sql = "select * from rewards_table where guild_id = $guild_id";

  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $arr[] = ['level'=>$row['level'], 'role_id'=>$row['role_id'], 'message_text'=>$row['message_text']];
    }
  }

  return $arr;
}

function getSounds($guild_id, $user = null) {
  global $conn;
  global $beebot;

  $query_add = "";
  if($user != null){
    $query_add = "AND user_id = $user";
  }

  $arr = [];
  $sql = "select * from sound where guild_id = $guild_id  $query_add";
  $result = $conn->query($sql);

  if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
      $arr[$row['id']] = ['name'=> $row['name'], 'extension'=> $row['extension'], 'user_id'=> $row['user_id']];
    }
  }

  return $arr;
}


//$conn->close();
?>