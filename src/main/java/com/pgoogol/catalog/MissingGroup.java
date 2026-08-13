package com.pgoogol.catalog;

/**
 * Grupa braków, po której da się filtrować katalog (M5.6). Wartości odpowiadają
 * grupom pól wzbogacania z D11 ({@code enrichment.FieldGroup}) i tym samym
 * warunkom „missing", którymi liczy się braki w zakładce Wzbogacanie — powtórzone
 * tutaj, bo {@code catalog} jest modułem niższym niż {@code enrichment}
 * i nie może o nim wiedzieć.
 *
 * <p>{@link #ANY} to „brakuje czegokolwiek z trzech grup" — dokładnie to,
 * co w tabeli biblioteki nosi znacznik „do wzbogacenia".</p>
 */
public enum MissingGroup {

    METADATA,
    AUDIO,
    AI,
    ANY
}
