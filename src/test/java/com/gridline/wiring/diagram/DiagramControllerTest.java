package com.gridline.wiring.diagram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridline.wiring.common.ApiException;
import com.gridline.wiring.diagram.dto.DiagramDetail;
import com.gridline.wiring.diagram.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图纸接口的 Web 层测试。
 *
 * <p>使用 {@link WebMvcTest} 只加载控制器、全局异常处理器和 MVC 配置，Service 通过
 * Mockito 模拟，因此不依赖数据库。测试重点不是业务细节，而是验证所有接口确实返回
 * 统一的 {@code R} 包装，并且 HTTP 状态码、success/code、data.result、校验字段
 * 映射在成功与失败路径上都符合约定。</p>
 */
@WebMvcTest(DiagramController.class)
class DiagramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramService diagramService;

    @Test
    void listShouldWrapPageResponseInR() throws Exception {
        when(diagramService.list("", 0, 24))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 24, 0));

        mockMvc.perform(get("/api/diagrams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.result.items").isArray())
                .andExpect(jsonPath("$.data.result.total").value(0));
    }

    @Test
    void createShouldReturn201AndWrappedDetail() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DiagramDetail detail = new DiagramDetail(1L, "测试图纸", "WD-20261103-ABCD", "10kV",
                null, 1, mapper.createObjectNode(), null,
                LocalDateTime.now(), LocalDateTime.now());
        when(diagramService.create(org.mockito.ArgumentMatchers.any())).thenReturn(detail);

        mockMvc.perform(post("/api/diagrams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试图纸\",\"voltageLevel\":\"10kV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("图纸创建成功"))
                .andExpect(jsonPath("$.data.result.id").value(1))
                .andExpect(jsonPath("$.data.result.code").value("WD-20261103-ABCD"));
    }

    @Test
    void createShouldExposeValidationFieldsUnderData() throws Exception {
        mockMvc.perform(post("/api/diagrams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("提交的数据校验未通过"))
                .andExpect(jsonPath("$.data.fields.name").value("图纸名称不能为空"));
    }

    @Test
    void getShouldReturnRBodyOnNotFound() throws Exception {
        when(diagramService.get(999L))
                .thenThrow(ApiException.notFound("图纸不存在或已被删除"));

        mockMvc.perform(get("/api/diagrams/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("图纸不存在或已被删除"));
    }

    @Test
    void deleteShouldReturnSuccessWithoutResult() throws Exception {
        doNothing().when(diagramService).delete(1L);

        mockMvc.perform(delete("/api/diagrams/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("图纸已删除"))
                .andExpect(jsonPath("$.data.result").doesNotExist());
    }

    @Test
    void unsupportedMethodShouldReturn405() throws Exception {
        mockMvc.perform(post("/api/diagrams/stats"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    void invalidQueryParameterShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/diagrams").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求参数缺失或格式错误"));
    }

    @Test
    void missingRequestParameterShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/diagrams/1/versions/2/restore"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求参数缺失或格式错误"));
    }
}
