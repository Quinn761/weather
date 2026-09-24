package com.weatherhub.ai.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetOfficialAlertsToolTest {

    private static final String TIANJIN_WARNING = "天津市 天津市气象台发布雷电黄色预警 雷电 黄色";

    @Test
    void matchesRegionWhenTheQuestionContainsGenericAlertWords() {
        assertTrue(GetOfficialAlertsTool.matches(TIANJIN_WARNING, "天津的预警信息"));
    }

    @Test
    void doesNotMatchAnotherRegion() {
        assertFalse(GetOfficialAlertsTool.matches(TIANJIN_WARNING, "海南的预警信息"));
    }

    @Test
    void treatsNationalWarningQueryAsAnAllRegionsQuery() {
        assertTrue(GetOfficialAlertsTool.matches(TIANJIN_WARNING, "全国灾害预警"));
    }
}
