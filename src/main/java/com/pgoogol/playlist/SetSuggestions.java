package com.pgoogol.playlist;

import java.util.List;

/**
 * Kandydaci na jedną lukę w secie (M4.4, D32) razem z pozycją, której dotyczą —
 * żądanie może ją pominąć („na koniec"), a front potrzebuje jej wprost, żeby
 * wiedzieć, gdzie wstawić wybrany utwór.
 */
public record SetSuggestions(int position, List<SetSuggestion> suggestions) {

}
