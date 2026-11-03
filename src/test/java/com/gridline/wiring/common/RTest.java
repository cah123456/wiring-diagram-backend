package com.gridline.wiring.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应包装的单元测试。
 *
 * <p>重点保证 code、message、success 三者的契约稳定：成功固定 20000 且
 * success=true，失败默认 20001 且 success=false，系统异常使用 20005。
 * 这些值一旦变化，前端基于 success 的判断和错误码展示都会受影响。</p>
 */
class RTest {

    @Test
    void okShouldUseSuccessCodeAndMessage() {
        R response = R.ok();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void okShouldCarryResultUnderStableKey() {
        R response = R.ok("图纸创建成功").result(Map.of("id", 1));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("图纸创建成功");
        assertThat(response.getData()).containsEntry("result", Map.of("id", 1));
    }

    @Test
    void errorShouldUseErrorCodeAndCustomMessage() {
        R response = R.error().message("图纸不存在");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("图纸不存在");
    }

    @Test
    void systemErrorShouldUseSystemCode() {
        R response = R.error(ResultCode.SYSTEM_ERROR);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }

    @Test
    void dataShouldMergeAdditionalEntries() {
        R response = R.error().data(Map.of("fields", Map.of("name", "不能为空")))
                .data("traceId", "abc");

        assertThat(response.getData()).containsKeys("fields", "traceId");
    }
}
