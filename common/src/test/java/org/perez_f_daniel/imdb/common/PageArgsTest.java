package org.perez_f_daniel.imdb.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageArgsTest {

  @Test
  void passesThroughValidArgs() {
    assertThat(PageArgs.clamp(50, 10, 50, 200)).isEqualTo(new PageArgs(50, 10));
  }

  @Test
  void clampsLimitToMax() {
    assertThat(PageArgs.clamp(9999, 0, 50, 200)).isEqualTo(new PageArgs(200, 0));
  }

  @Test
  void clampsLimitToAtLeastOne() {
    assertThat(PageArgs.clamp(0, 0, 50, 200)).isEqualTo(new PageArgs(1, 0));
    assertThat(PageArgs.clamp(-5, 0, 50, 200)).isEqualTo(new PageArgs(1, 0));
  }

  @Test
  void clampsNegativeOffsetToZero() {
    assertThat(PageArgs.clamp(10, -1, 50, 200)).isEqualTo(new PageArgs(10, 0));
  }

  @Test
  void nullsUseDefaults() {
    assertThat(PageArgs.clamp(null, null, 50, 200)).isEqualTo(new PageArgs(50, 0));
  }
}
