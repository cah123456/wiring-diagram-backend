package com.gridline.wiring.diagram;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridline.wiring.common.ResultCode;
import com.gridline.wiring.controller.DiagramController;
import com.gridline.wiring.exception.BusinessException;
import com.gridline.wiring.service.DiagramService;
import com.gridline.wiring.vo.DiagramSummaryVO;
import com.gridline.wiring.vo.DiagramVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

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
    void listShouldWrapPageInR() throws Exception {
        Page<DiagramSummaryVO> page = new Page<>(1, 10);
        page.setTotal(0);
        when(diagramService.listDiagrams(org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/diagrams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.result.records").isArray())
                .andExpect(jsonPath("$.data.result.total").value(0))
                .andExpect(jsonPath("$.data.result.current").value(1));
    }

    @Test
    void createShouldReturn201AndWrappedDetail() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DiagramVO detail = new DiagramVO();
        detail.setId(1L);
        detail.setName("测试图纸");
        detail.setCode("WD-20261103-ABCD");
        detail.setVoltageLevel("10kV");
        detail.setDocument(mapper.createObjectNode());
        detail.setCreatedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        when(diagramService.createDiagram(org.mockito.ArgumentMatchers.any())).thenReturn(detail);

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
        when(diagramService.getDetail(999L))
                .thenThrow(BusinessException.notFound("图纸不存在或已被删除"));

        mockMvc.perform(get("/api/diagrams/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ResultCode.ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("图纸不存在或已被删除"));
    }

    @Test
    void deleteShouldReturnSuccessWithoutResult() throws Exception {
        doNothing().when(diagramService).deleteDiagram(1L);

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
        mockMvc.perform(get("/api/diagrams").param("pageNum", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("提交的数据校验未通过"))
                .andExpect(jsonPath("$.data.fields.pageNum").exists());
    }

    @Test
    void invalidJsonBodyShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/diagrams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("请求内容格式错误，请检查 JSON 数据"));
    }
}
