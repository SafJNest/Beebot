<?php

function print_html($string, $return = false, $nesting_level = 0)
{
    $trace = debug_backtrace();
    $heading = isset($trace[$nesting_level]) ? ($trace[$nesting_level]['file'] . ":" . $trace[$nesting_level]['line'] . "\n") : '';
    $out = '<pre>'
        . $heading
        . print_r($string, true) . '</pre>';
    if ($return) {
        return $out;
    }
    echo $out;
}

?>