<?php
session_start();
$response = $_POST['response'];
$response['tokenType'] = $_POST['tokenType'];
$response['accessToken'] = $_POST['accessToken'];
$_SESSION['user_data'] = $response;
?>