<?php
require __DIR__ . '../../assets/php/database.php';
require __DIR__ . '../../assets/php/utilities.php';

session_start();

$data = $_SESSION['user_data'];
$user_id = $data['id'];
$guild = $_SESSION['guild_id'];

$result = getGoodbyeMessage($guild);
print_html($result);


?>