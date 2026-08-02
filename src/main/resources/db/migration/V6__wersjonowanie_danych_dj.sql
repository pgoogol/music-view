-- V6: blokada optymistyczna na danych, które ma więcej niż jeden pisarz (D29).
--
-- Wersjonujemy library_entry (uwagi, tagi, oceny) i playlist (skład i kolejność
-- setu) — to jedyne dane, których nie odtworzy żadne API, a mają realnie dwóch
-- pisarzy: dwie karty przeglądarki, laptop i telefon.
--
-- track_catalog świadomie zostaje bez wersji: pisze do niego wyłącznie job
-- wzbogacania (jeden pisarz), a konflikt kosztowałby tam restart całego chunka.
--
-- Wersja siedzi na agregacie: zmiana wierszy playlist_track podbija
-- playlist.version (jawny OPTIMISTIC_FORCE_INCREMENT w PlaylistService), bo
-- reorder i tak dotyczy całej playlisty (D21 wymaga permutacji).

ALTER TABLE library_entry ADD COLUMN version integer NOT NULL DEFAULT 0;
ALTER TABLE playlist      ADD COLUMN version integer NOT NULL DEFAULT 0;
