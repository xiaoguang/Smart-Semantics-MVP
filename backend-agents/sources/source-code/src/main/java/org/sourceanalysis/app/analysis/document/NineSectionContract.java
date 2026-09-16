package org.sourceanalysis.app.analysis.document;

import java.util.List;

/** Immutable section order needed to reopen and rerender historical nine-section reports. */
final class NineSectionContract {

  private static final List<String> TITLES =
      List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

  private NineSectionContract() {}

  static List<String> titles() {
    return TITLES;
  }
}
