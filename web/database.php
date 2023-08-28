<?php

$servername = "***REMOVED***";
$username = "***REMOVED***";
$password = "***REMOVED***";
$dbname = "***REMOVED***";

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

//$conn->close();
?>