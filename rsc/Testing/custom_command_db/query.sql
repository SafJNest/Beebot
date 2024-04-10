-- phpMyAdmin SQL Dump
-- version 4.8.5
-- https://www.phpmyadmin.net/
--
-- Host: ***REMOVED***
-- Creato il: Apr 10, 2024 alle 19:50
-- Versione del server: 10.2.41-MariaDB-log
-- Versione PHP: 7.2.34

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `***REMOVED***`
--

-- --------------------------------------------------------

--
-- Struttura della tabella `commands`
--

CREATE TABLE `commands` (
  `id` int(10) UNSIGNED NOT NULL,
  `guild_id` varchar(19) NOT NULL,
  `user_id` varchar(19) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `slash` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dump dei dati per la tabella `commands`
--

INSERT INTO `commands` (`id`, `guild_id`, `user_id`, `name`, `description`, `slash`) VALUES
(1, '608967318789160970', '383358222972616705', 'nuovavita', 'tier god song', 1),
(2, '608967318789160970', '383358222972616705', 'buconero', 'cancella una stanza', 1);

-- --------------------------------------------------------

--
-- Struttura della tabella `command_option`
--

CREATE TABLE `command_option` (
  `id` int(10) UNSIGNED NOT NULL,
  `command_id` int(10) UNSIGNED NOT NULL,
  `key` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `required` tinyint(4) NOT NULL,
  `type` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dump dei dati per la tabella `command_option`
--

INSERT INTO `command_option` (`id`, `command_id`, `key`, `description`, `required`, `type`) VALUES
(2, 2, 'stanza', 'butta una stanza into blackihole', 1, 7);

-- --------------------------------------------------------

--
-- Struttura della tabella `command_option_value`
--

CREATE TABLE `command_option_value` (
  `id` int(10) UNSIGNED NOT NULL,
  `option_id` int(10) UNSIGNED NOT NULL,
  `key` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task`
--

CREATE TABLE `command_task` (
  `id` int(10) UNSIGNED NOT NULL,
  `command_id` int(10) UNSIGNED NOT NULL,
  `order` tinyint(4) NOT NULL,
  `type` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dump dei dati per la tabella `command_task`
--

INSERT INTO `command_task` (`id`, `command_id`, `order`, `type`) VALUES
(1, 1, 1, 0),
(2, 2, 1, 1);

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task_message`
--

CREATE TABLE `command_task_message` (
  `id` int(10) UNSIGNED NOT NULL,
  `task_value_id` int(10) UNSIGNED NOT NULL,
  `message` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dump dei dati per la tabella `command_task_message`
--

INSERT INTO `command_task_message` (`id`, `task_value_id`, `message`) VALUES
(1, 1, 'ehi\nprimo freestyle\n2 0 2 0\nnuova vita\nsto in cameretta a scrivere co\' \'na matita\nco\' \'ste parole la scena viene demolita\ne la vinco io questa cazzo di partita\nspacco la traccia si fra, ti spacco la faccia\ntu quano rappi sembri arrugginito\nle tue rime così squallide che rimango basito\ndici tanto che il tuo rap sfonda\nmi sa che con tutte le cavolate che spari affonda\nil mio rap ti sfonda\nil tuo sprofonda...\nseh fra\nil tuo sprofonda...\nehi..\nokay che con queste rime t\'ho già rotto il culo\ne t\'assicuro che per te non c\'è futuro quindi te censuro\ne se vuoi fare una cosa bona bevete er cianuro\nte e pornhub sembrate ciccio col suo paguro\nsente l\'attacco che te sferro ti piace predere il ferro nell\'ano da tizi come tiziano ferro\nallora vuoi pure la fama ti sputo in faccia tipo lama\nio vado, sono il re del rap ancora, chiama.');

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task_value`
--

CREATE TABLE `command_task_value` (
  `id` int(10) UNSIGNED NOT NULL,
  `task_id` int(10) UNSIGNED NOT NULL,
  `value` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dump dei dati per la tabella `command_task_value`
--

INSERT INTO `command_task_value` (`id`, `task_id`, `value`) VALUES
(1, 1, 1);

--
-- Indici per le tabelle scaricate
--

--
-- Indici per le tabelle `commands`
--
ALTER TABLE `commands`
  ADD PRIMARY KEY (`id`);

--
-- Indici per le tabelle `command_option`
--
ALTER TABLE `command_option`
  ADD PRIMARY KEY (`id`);

--
-- Indici per le tabelle `command_option_value`
--
ALTER TABLE `command_option_value`
  ADD PRIMARY KEY (`id`);

--
-- Indici per le tabelle `command_task`
--
ALTER TABLE `command_task`
  ADD PRIMARY KEY (`id`);

--
-- Indici per le tabelle `command_task_message`
--
ALTER TABLE `command_task_message`
  ADD PRIMARY KEY (`id`);

--
-- Indici per le tabelle `command_task_value`
--
ALTER TABLE `command_task_value`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT per le tabelle scaricate
--

--
-- AUTO_INCREMENT per la tabella `commands`
--
ALTER TABLE `commands`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT per la tabella `command_option`
--
ALTER TABLE `command_option`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT per la tabella `command_option_value`
--
ALTER TABLE `command_option_value`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT per la tabella `command_task`
--
ALTER TABLE `command_task`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT per la tabella `command_task_message`
--
ALTER TABLE `command_task_message`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT per la tabella `command_task_value`
--
ALTER TABLE `command_task_value`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
