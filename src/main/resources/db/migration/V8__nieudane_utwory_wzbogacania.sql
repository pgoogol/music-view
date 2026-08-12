-- V8: utwory, które padły w trakcie wzbogacania (D37).
--
-- Job nie przerywa się już na pierwszym błędzie — przechodzi przez całą listę
-- i pomija to, co się nie udało. Bez zapisania powodów „pominięte: 37" nie
-- niesie żadnej informacji: nie wiadomo, czy padł jeden serwis, czy 37 razy
-- to samo. Licznik pominięć trzyma sam Spring Batch (write_skip_count), więc
-- tutaj interesują nas wyłącznie powody.
--
-- Bez klucza obcego do BATCH_JOB_EXECUTION: te tabele należą do Spring Batcha
-- i to on decyduje o ich czyszczeniu; nie chcemy blokować mu kasowania historii
-- naszym więzem. Osierocone wiersze sprząta usunięcie po job_execution_id.

CREATE TABLE enrichment_failure (
    id                bigserial   PRIMARY KEY,
    job_execution_id  bigint      NOT NULL,
    spotify_id        varchar(64) NOT NULL,
    reason            text        NOT NULL,
    failed_at         timestamptz NOT NULL
);

CREATE INDEX idx_enrichment_failure_execution ON enrichment_failure (job_execution_id);
