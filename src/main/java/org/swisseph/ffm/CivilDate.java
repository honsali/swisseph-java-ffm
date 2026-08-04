package org.swisseph.ffm;

/** Civil date and decimal hour returned by {@code swe_revjul()}. */
public record CivilDate(int year, int month, int day, double hour) {
}
