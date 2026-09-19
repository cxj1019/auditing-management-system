package com.accounting.firm.common.search;

import com.accounting.firm.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 全局搜索（登录即可用，结果按各模块数据权限过滤） */
@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping
    public ApiResult<List<Map<String, Object>>> search(@RequestParam("keyword") String keyword) {
        return ApiResult.success(globalSearchService.search(keyword));
    }
}
