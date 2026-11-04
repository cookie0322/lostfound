/**
 * 表示层适配器：Controller、请求响应 DTO、统一返回体与全局异常出口。
 *
 * <p>对应架构表 3-2 的"表示层：前端模块"在这一侧的实现部分，不新增模块。
 * 依赖约束（架构 3.3.3 ①）：只能调用业务层（auth / claim），不得直接访问
 * Mapper 或拼 SQL。
 */
package edu.campus.lostfound.web;
