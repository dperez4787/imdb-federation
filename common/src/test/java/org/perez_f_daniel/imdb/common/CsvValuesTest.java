package org.perez_f_daniel.imdb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvValuesTest {

  @Test
  void splitsCommaSeparatedValues() {
    assertThat(CsvValues.split("Documentary,Short")).containsExactly("Documentary", "Short");
  }

  @Test
  void singleValueYieldsSingletonList() {
    assertThat(CsvValues.split("nm0005690")).containsExactly("nm0005690");
  }

  @Test
  void trimsWhitespaceAndDropsBlankSegments() {
    assertThat(CsvValues.split(" a , ,b,")).containsExactly("a", "b");
  }

  @Test
  void absentOrBlankYieldsNull() {
    assertThat(CsvValues.split(null)).isNull();
    assertThat(CsvValues.split("")).isNull();
    assertThat(CsvValues.split("  ")).isNull();
    assertThat(CsvValues.split(",,")).isNull();
  }

  @Test
  void resultIsImmutable() {
    List<String> values = CsvValues.split("a,b");
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> values.add("c"));
  }
}
