-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 51.222.27.228
-- Creato il: Feb 05, 2025 alle 21:01
-- Versione del server: 10.11.6-MariaDB-0+deb12u1
-- Versione PHP: 8.2.26

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `berbit`
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
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dump dei dati per la tabella `commands`
--

INSERT INTO `commands` (`id`, `guild_id`, `user_id`, `name`, `description`, `slash`) VALUES
(1, '608967318789160970', '383358222972616705', 'nuovavita', 'tier god song', 1),
(2, '608967318789160970', '383358222972616705', 'buconero', 'cancella una stanza', 1),
(3, '474935164451946506', '383358222972616705', 'nuovavita', 'prima canzone dell\'epria', 1),
(4, '474935164451946506', '383358222972616705', 'uominipolvere', 'Second Insane Song by Epria', 1),
(5, '474935164451946506', '383358222972616705', 'impara', 'Third Insane Song by Epria', 1),
(6, '474935164451946506', '383358222972616705', 'cittadi', 'The best song ever made by Epria', 1),
(7, '608967318789160970', '383358222972616705', 'epria', 'Full Epria\'s discography', 1),
(8, '474935164451946506', '383358222972616705', 'draven', 'POOOOOOOOOOM', 1),
(10, '474935164451946506', '383358222972616705', 'epria', 'Full Epria\'s discography', 1),
(11, '1191835670445035571', '383358222972616705', 'tooltipmultiselectgroup', 'eee', 1),
(12, '1191835670445035571', '383358222972616705', 'tooltipselectgroup', 'eee', 1);

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
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task`
--

CREATE TABLE `command_task` (
  `id` int(10) UNSIGNED NOT NULL,
  `command_id` int(10) UNSIGNED NOT NULL,
  `order` tinyint(4) NOT NULL,
  `type` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dump dei dati per la tabella `command_task`
--

INSERT INTO `command_task` (`id`, `command_id`, `order`, `type`) VALUES
(1, 1, 1, 0),
(2, 2, 1, 1),
(3, 2, 2, 0),
(4, 3, 1, 0),
(5, 4, 0, 0),
(6, 5, 0, 0),
(7, 6, 0, 0),
(8, 7, 0, 0),
(9, 7, 1, 0),
(10, 7, 2, 0),
(11, 7, 3, 0),
(12, 8, 0, 2),
(18, 10, 1, 0),
(19, 10, 2, 0),
(20, 10, 3, 0),
(21, 10, 4, 0),
(22, 8, 1, 0),
(23, 11, 1, 0),
(24, 12, 1, 0);

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task_message`
--

CREATE TABLE `command_task_message` (
  `id` int(10) UNSIGNED NOT NULL,
  `task_value_id` int(10) UNSIGNED NOT NULL,
  `message` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dump dei dati per la tabella `command_task_message`
--

INSERT INTO `command_task_message` (`id`, `task_value_id`, `message`) VALUES
(1, 1, 'ehi\nprimo freestyle\n2 0 2 0\nnuova vita\nsto in cameretta a scrivere co\' \'na matita\nco\' \'ste parole la scena viene demolita\ne la vinco io questa cazzo di partita\nspacco la traccia si fra, ti spacco la faccia\ntu quano rappi sembri arrugginito\nle tue rime così squallide che rimango basito\ndici tanto che il tuo rap sfonda\nmi sa che con tutte le cavolate che spari affonda\nil mio rap ti sfonda\nil tuo sprofonda...\nseh fra\nil tuo sprofonda...\nehi..\nokay che con queste rime t\'ho già rotto il culo\ne t\'assicuro che per te non c\'è futuro quindi te censuro\ne se vuoi fare una cosa bona bevete er cianuro\nte e pornhub sembrate ciccio col suo paguro\nsente l\'attacco che te sferro ti piace predere il ferro nell\'ano da tizi come tiziano ferro\nallora vuoi pure la fama ti sputo in faccia tipo lama\nio vado, sono il re del rap ancora, chiama.'),
(2, 3, 'sgozzing eseguito con sucesso'),
(3, 4, 'ehi\nprimo freestyle\n2 0 2 0\nnuova vita\nsto in cameretta a scrivere co\' \'na matita\nco\' \'ste parole la scena viene demolita\ne la vinco io questa cazzo di partita\nspacco la traccia si fra, ti spacco la faccia\ntu quano rappi sembri arrugginito\nle tue rime così squallide che rimango basito\ndici tanto che il tuo rap sfonda\nmi sa che con tutte le cavolate che spari affonda\nil mio rap ti sfonda\nil tuo sprofonda...\nseh fra\nil tuo sprofonda...\nehi..\nokay che con queste rime t\'ho già rotto il culo\ne t\'assicuro che per te non c\'è futuro quindi te censuro\ne se vuoi fare una cosa bona bevete er cianuro\nte e pornhub sembrate ciccio col suo paguro\nsente l\'attacco che te sferro ti piace predere il ferro nell\'ano da tizi come tiziano ferro\nallora vuoi pure la fama ti sputo in faccia tipo lama\nio vado, sono il re del rap ancora, chiama.'),
(4, 5, 'ehi...\nsenti...\nsono qui come un coglione a sfogarmi\nlo faccio perché non so se ridere o disperarmi\ni sentimenti son\' come la polvere\nrimangono nel cuore e ti fa scorrere\nla luce della speranza quella che gli uomini rinchiudono nella stanza\nscusate se faccio rime da cazzaro, non sono nè rapper nè metallaro\nquello che sono non è per nulla chiaro\nma la metrica come un faro ad illuminare il mio cammino\nquello da casa mia allo strozzino\nquello che mi separa da uno spacciatore è che voglio vivere senza rossore\nvoglio dire ai miei figli: \"nella vita non fate i conigli, quando vostro padre rum vive quelle che a casa vostra son finite\nperché gli uomini son polvere che nel vento nel deserto vedi sciogliere\"'),
(5, 6, 'Impara\nPenso al futuro ma vivo il presente\nNon ha senso fare progetti se non sai fare niente\nFino a ieri annegavo tra i dubbi, le mie insicurezze\nMi hanno imparato a trasformare i sogni in certezze\nHo scoperto il rap da poco\nOra lo scrivo come se fosse un gioco\nSfodero un sorriso davanti all\'obiettivo, scelgo una posa e metto a fuoco\nE\' bello pensare a quanto è facile sfondare\nNel mondo della musica non ricevi senza dare\nAccendi la radio senti una canzone\nHai l\'impressione che anche per te ci sia un\'occasione\nMa non serve a nulla il talento se c\'è corruzione\nLa Mafia è un vulcano in continua eruzione\nEhy...\nEhy...\nVedi cio che vuoi ma la realtà è diversa\nSe pensi che si avrà giustizia è una scommessa persa\nNon c\'è bisogno che mi ringrazi, per me è un passatempo\nIn futuro sarà un lavoro, perchè continuo a crederci e non penso al tempo\nOra, se vuoi essere onesto dammi ciò che mi spetta\nPerchè prima degli altri ho raggiunto la vetta'),
(6, 7, 'ti parlo della mia città\ndove per ogni ragazzino della mia età\nl’unica persona che conta \nè avere la pistola pronta\nma fate come  minchia volete\nanzi continuate così fin quando non vi stancherete\ne restate chiusi nella vostra mentalità\nnella vostra provincialità\ncomplici della criminalità e della malavita\nse pensate così non avete capito un cazzo  della vita\ne leccate ancora il  culo ai vostri amici\nChe in realtà sono figli dei nostri nemici\ne non venirmi a dire “Oh, cazzo dici?!”\nDico solo quello che penso di questa città\ne dico che questa città è la feccia dell’umanità\nanche se a chiamarla così gli dò già troppa importanza\nraga è solo un paesino di me.rda con molta ignoranza\nma lo capite o no chi è importante?\nnon è quel figlio di puttana che ammazza gente avvolte innocente\nio li definirei di più malati di mente!\nma forse sono l’unico stron.zo sfigato a pensarla così\nraga ho capito me ne andrò via di qui'),
(7, 8, 'ehi\nprimo freestyle\n2 0 2 0\nnuova vita\nsto in cameretta a scrivere co\' \'na matita\nco\' \'ste parole la scena viene demolita\ne la vinco io questa cazzo di partita\nspacco la traccia si fra, ti spacco la faccia\ntu quano rappi sembri arrugginito\nle tue rime così squallide che rimango basito\ndici tanto che il tuo rap sfonda\nmi sa che con tutte le cavolate che spari affonda\nil mio rap ti sfonda\nil tuo sprofonda...\nseh fra\nil tuo sprofonda...\nehi..\nokay che con queste rime t\'ho già rotto il culo\ne t\'assicuro che per te non c\'è futuro quindi te censuro\ne se vuoi fare una cosa bona bevete er cianuro\nte e pornhub sembrate ciccio col suo paguro\nsente l\'attacco che te sferro ti piace predere il ferro nell\'ano da tizi come tiziano ferro\nallora vuoi pure la fama ti sputo in faccia tipo lama\nio vado, sono il re del rap ancora, chiama.'),
(8, 9, 'ehi...\nsenti...\nsono qui come un coglione a sfogarmi\nlo faccio perché non so se ridere o disperarmi\ni sentimenti son\' come la polvere\nrimangono nel cuore e ti fa scorrere\nla luce della speranza quella che gli uomini rinchiudono nella stanza\nscusate se faccio rime da cazzaro, non sono nè rapper nè metallaro\nquello che sono non è per nulla chiaro\nma la metrica come un faro ad illuminare il mio cammino\nquello da casa mia allo strozzino\nquello che mi separa da uno spacciatore è che voglio vivere senza rossore\nvoglio dire ai miei figli: \"nella vita non fate i conigli, quando vostro padre rum vive quelle che a casa vostra son finite\nperché gli uomini son polvere che nel vento nel deserto vedi sciogliere\"'),
(9, 10, 'Impara\nPenso al futuro ma vivo il presente\nNon ha senso fare progetti se non sai fare niente\nFino a ieri annegavo tra i dubbi, le mie insicurezze\nMi hanno imparato a trasformare i sogni in certezze\nHo scoperto il rap da poco\nOra lo scrivo come se fosse un gioco\nSfodero un sorriso davanti all\'obiettivo, scelgo una posa e metto a fuoco\nE\' bello pensare a quanto è facile sfondare\nNel mondo della musica non ricevi senza dare\nAccendi la radio senti una canzone\nHai l\'impressione che anche per te ci sia un\'occasione\nMa non serve a nulla il talento se c\'è corruzione\nLa Mafia è un vulcano in continua eruzione\nEhy...\nEhy...\nVedi cio che vuoi ma la realtà è diversa\nSe pensi che si avrà giustizia è una scommessa persa\nNon c\'è bisogno che mi ringrazi, per me è un passatempo\nIn futuro sarà un lavoro, perchè continuo a crederci e non penso al tempo\nOra, se vuoi essere onesto dammi ciò che mi spetta\nPerchè prima degli altri ho raggiunto la vetta'),
(10, 11, 'ti parlo della mia città\ndove per ogni ragazzino della mia età\nl’unica persona che conta \nè avere la pistola pronta\nma fate come  minchia volete\nanzi continuate così fin quando non vi stancherete\ne restate chiusi nella vostra mentalità\nnella vostra provincialità\ncomplici della criminalità e della malavita\nse pensate così non avete capito un cazzo  della vita\ne leccate ancora il  culo ai vostri amici\nChe in realtà sono figli dei nostri nemici\ne non venirmi a dire “Oh, cazzo dici?!”\nDico solo quello che penso di questa città\ne dico che questa città è la feccia dell’umanità\nanche se a chiamarla così gli dò già troppa importanza\nraga è solo un paesino di me.rda con molta ignoranza\nma lo capite o no chi è importante?\nnon è quel figlio di puttana che ammazza gente avvolte innocente\nio li definirei di più malati di mente!\nma forse sono l’unico stron.zo sfigato a pensarla così\nraga ho capito me ne andrò via di qui'),
(11, 14, 'ehi\nprimo freestyle\n2 0 2 0\nnuova vita\nsto in cameretta a scrivere co\' \'na matita\nco\' \'ste parole la scena viene demolita\ne la vinco io questa cazzo di partita\nspacco la traccia si fra, ti spacco la faccia\ntu quano rappi sembri arrugginito\nle tue rime così squallide che rimango basito\ndici tanto che il tuo rap sfonda\nmi sa che con tutte le cavolate che spari affonda\nil mio rap ti sfonda\nil tuo sprofonda...\nseh fra\nil tuo sprofonda...\nehi..\nokay che con queste rime t\'ho già rotto il culo\ne t\'assicuro che per te non c\'è futuro quindi te censuro\ne se vuoi fare una cosa bona bevete er cianuro\nte e pornhub sembrate ciccio col suo paguro\nsente l\'attacco che te sferro ti piace predere il ferro nell\'ano da tizi come tiziano ferro\nallora vuoi pure la fama ti sputo in faccia tipo lama\nio vado, sono il re del rap ancora, chiama.'),
(12, 15, 'ehi...\nsenti...\nsono qui come un coglione a sfogarmi\nlo faccio perché non so se ridere o disperarmi\ni sentimenti son\' come la polvere\nrimangono nel cuore e ti fa scorrere\nla luce della speranza quella che gli uomini rinchiudono nella stanza\nscusate se faccio rime da cazzaro, non sono nè rapper nè metallaro\nquello che sono non è per nulla chiaro\nma la metrica come un faro ad illuminare il mio cammino\nquello da casa mia allo strozzino\nquello che mi separa da uno spacciatore è che voglio vivere senza rossore\nvoglio dire ai miei figli: \"nella vita non fate i conigli, quando vostro padre rum vive quelle che a casa vostra son finite\nperché gli uomini son polvere che nel vento nel deserto vedi sciogliere\"'),
(13, 16, 'Impara\nPenso al futuro ma vivo il presente\nNon ha senso fare progetti se non sai fare niente\nFino a ieri annegavo tra i dubbi, le mie insicurezze\nMi hanno imparato a trasformare i sogni in certezze\nHo scoperto il rap da poco\nOra lo scrivo come se fosse un gioco\nSfodero un sorriso davanti all\'obiettivo, scelgo una posa e metto a fuoco\nE\' bello pensare a quanto è facile sfondare\nNel mondo della musica non ricevi senza dare\nAccendi la radio senti una canzone\nHai l\'impressione che anche per te ci sia un\'occasione\nMa non serve a nulla il talento se c\'è corruzione\nLa Mafia è un vulcano in continua eruzione\nEhy...\nEhy...\nVedi cio che vuoi ma la realtà è diversa\nSe pensi che si avrà giustizia è una scommessa persa\nNon c\'è bisogno che mi ringrazi, per me è un passatempo\nIn futuro sarà un lavoro, perchè continuo a crederci e non penso al tempo\nOra, se vuoi essere onesto dammi ciò che mi spetta\nPerchè prima degli altri ho raggiunto la vetta'),
(14, 17, 'ti parlo della mia città\ndove per ogni ragazzino della mia età\nl’unica persona che conta \nè avere la pistola pronta\nma fate come  minchia volete\nanzi continuate così fin quando non vi stancherete\ne restate chiusi nella vostra mentalità\nnella vostra provincialità\ncomplici della criminalità e della malavita\nse pensate così non avete capito un cazzo  della vita\ne leccate ancora il  culo ai vostri amici\nChe in realtà sono figli dei nostri nemici\ne non venirmi a dire “Oh, cazzo dici?!”\nDico solo quello che penso di questa città\ne dico che questa città è la feccia dell’umanità\nanche se a chiamarla così gli dò già troppa importanza\nraga è solo un paesino di me.rda con molta ignoranza\nma lo capite o no chi è importante?\nnon è quel figlio di puttana che ammazza gente avvolte innocente\nio li definirei di più malati di mente!\nma forse sono l’unico stron.zo sfigato a pensarla così\nraga ho capito me ne andrò via di qui'),
(15, 18, 'POOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOM'),
(17, 19, '```javascript\n/**\n    * lista:\n    * ID => ID elemento\n    * nome => nome elemento\n    * IDgruppo =>ID raggruppamento\n    * nome_gruppo => Nome Raggruppamento\n    *\n*/\nlet lista_rette = <?= json_encode($select_rette); ?>;\n$(\'.filtra_trattamenti\').data(\'id\', <?= json_encode($trattamenti_selezionati); ?>);\ntooltip_multiselect_generico_group($(\'.filtra_trattamenti\'), lista_rette, (filtro_rette) => {\n    inizia_ricerca_griglia_preseza();\n});\n```\n```html\n<div class=\"div_select__tooltip filtra_trattamenti\"    style=\"font-size:12px;width: 100%;\" ></div>\n```'),
(18, 20, '```javascript\n/**\n    * lista:\n    * ID => ID elemento\n    * nome => nome elemento\n    * IDgruppo =>ID raggruppamento\n    * nome_gruppo => Nome Raggruppamento\n    *\n*/\nlet lista_categorie = <?= json_encode($select_categoria) ?>;\ntooltip_select_generico_group($(\'.tooltip_alloggio\'), lista_categorie, (newIDcategoria) => {\n      gestrestcat(IDcategoria, 87, newIDcategoria, 10, 13);\n});\n```\n```html\n<div class=\"div_select__tooltip  tooltip_alloggio\" data-id=\"\' . $IDcliente . \'\" data-key=\"\'. $valore .\'\" style=\"font-size:12px;width:150px\"></div>\n```');

-- --------------------------------------------------------

--
-- Struttura della tabella `command_task_value`
--

CREATE TABLE `command_task_value` (
  `id` int(10) UNSIGNED NOT NULL,
  `task_id` int(10) UNSIGNED NOT NULL,
  `value` int(10) UNSIGNED NOT NULL,
  `from_option` tinyint(4) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dump dei dati per la tabella `command_task_value`
--

INSERT INTO `command_task_value` (`id`, `task_id`, `value`, `from_option`) VALUES
(1, 1, 1, 0),
(2, 2, 2, 1),
(3, 3, 2, 0),
(4, 4, 3, 0),
(5, 5, 0, 0),
(6, 6, 0, 0),
(7, 7, 0, 0),
(8, 8, 0, 0),
(9, 9, 0, 0),
(10, 10, 0, 0),
(11, 11, 0, 0),
(12, 12, 182, 0),
(14, 18, 0, 0),
(15, 19, 0, 0),
(16, 20, 0, 0),
(17, 21, 0, 0),
(18, 22, 0, 0),
(19, 23, 0, 0),
(20, 24, 0, 0);

--
-- Indici per le tabelle scaricate
--

--
-- Indici per le tabelle `commands`
--
ALTER TABLE `commands`
  ADD PRIMARY KEY (`id`),
  ADD KEY `commands_relation_1` (`guild_id`);

--
-- Indici per le tabelle `command_option`
--
ALTER TABLE `command_option`
  ADD PRIMARY KEY (`id`),
  ADD KEY `command_option_relation_1` (`command_id`);

--
-- Indici per le tabelle `command_option_value`
--
ALTER TABLE `command_option_value`
  ADD PRIMARY KEY (`id`),
  ADD KEY `command_option_value_relation_1` (`option_id`);

--
-- Indici per le tabelle `command_task`
--
ALTER TABLE `command_task`
  ADD PRIMARY KEY (`id`),
  ADD KEY `command_task_relation_1` (`command_id`);

--
-- Indici per le tabelle `command_task_message`
--
ALTER TABLE `command_task_message`
  ADD PRIMARY KEY (`id`),
  ADD KEY `command_task_message_relation_1` (`task_value_id`);

--
-- Indici per le tabelle `command_task_value`
--
ALTER TABLE `command_task_value`
  ADD PRIMARY KEY (`id`),
  ADD KEY `command_task_value_relation_1` (`task_id`);

--
-- AUTO_INCREMENT per le tabelle scaricate
--

--
-- AUTO_INCREMENT per la tabella `commands`
--
ALTER TABLE `commands`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

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
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=25;

--
-- AUTO_INCREMENT per la tabella `command_task_message`
--
ALTER TABLE `command_task_message`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

--
-- AUTO_INCREMENT per la tabella `command_task_value`
--
ALTER TABLE `command_task_value`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=21;

--
-- Limiti per le tabelle scaricate
--

--
-- Limiti per la tabella `commands`
--
ALTER TABLE `commands`
  ADD CONSTRAINT `commands_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Limiti per la tabella `command_option`
--
ALTER TABLE `command_option`
  ADD CONSTRAINT `command_option_relation_1` FOREIGN KEY (`command_id`) REFERENCES `commands` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Limiti per la tabella `command_option_value`
--
ALTER TABLE `command_option_value`
  ADD CONSTRAINT `command_option_value_relation_1` FOREIGN KEY (`option_id`) REFERENCES `command_option` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Limiti per la tabella `command_task`
--
ALTER TABLE `command_task`
  ADD CONSTRAINT `command_task_relation_1` FOREIGN KEY (`command_id`) REFERENCES `commands` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Limiti per la tabella `command_task_message`
--
ALTER TABLE `command_task_message`
  ADD CONSTRAINT `command_task_message_relation_1` FOREIGN KEY (`task_value_id`) REFERENCES `command_task_value` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Limiti per la tabella `command_task_value`
--
ALTER TABLE `command_task_value`
  ADD CONSTRAINT `command_task_value_relation_1` FOREIGN KEY (`task_id`) REFERENCES `command_task` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
