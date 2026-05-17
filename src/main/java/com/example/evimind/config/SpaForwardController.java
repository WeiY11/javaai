package com.example.evimind.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由转发控制器。
 * 将所有非 API、非静态资源的 GET 请求转发到 index.html，
 * 让 Vue Router 在前端接管路由处理。
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/login",
            "/knowledge-bases",
            "/documents",
            "/analysis",
            "/citations",
            "/notes"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
