-- V7: rodzina gatunkowa z pliku metryk (D34) — jedyna kolumna CSV, która do tej
-- pory trafiała prosto na track_catalog i nigdzie nie zostawała.
--
-- Bez zapisania jej obok reszty metryk nie da się odróżnić gatunku z pliku od
-- estymaty LLM-a, a więc nie da się dać plikowi pierwszeństwa: najbliższe
-- wzbogacanie AI i tak nadpisałoby wartość z pliku (tak działało do M4.5).
-- Trzymamy tekst enuma D8, jak w track_catalog.genre_family.

ALTER TABLE manual_metrics ADD COLUMN genre_family varchar(32);
